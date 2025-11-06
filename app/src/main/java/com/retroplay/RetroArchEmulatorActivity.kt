package com.retroplay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.view.KeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.ChainStyle
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKit
import gg.padkit.inputevents.InputEvent
import gg.padkit.ids.Id
import androidx.compose.ui.util.lerp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * RetroArch Emulator Activity
 * 
 * Features:
 * - Jetpack Compose UI for dialogs/menus
 * - Pure RetroArch overlays (Android layouts)
 * - LibretroDroid native cores (ARM64)
 * - NO Radial/Lemuroid gamepads
 */
class RetroArchEmulatorActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "RetroArchEmulator"
        private const val CRASH_PREFS = "core_crash_detection"
        private const val KEY_LAST_GAME = "last_game_path"
        private const val KEY_LAST_CORE = "last_core_attempted"
        private const val KEY_TIMESTAMP = "crash_timestamp"
        private const val CRASH_TIMEOUT_MS = 2000L // 2 secondes pour considérer un crash
        
        /**
         * Convertit le nom technique du core en nom d'affichage lisible
         */
        private fun getCoreDisplayName(coreFileName: String): String {
            val coreName = coreFileName.replace("_libretro_android.so", "")
            return when (coreName.lowercase()) {
                "fbneo" -> "FBNeo"
                "mame2003_plus" -> "MAME 2003 Plus"
                "mame2003" -> "MAME 2003"
                "mame2010" -> "MAME 2010"
                "fceumm" -> "FCEUmm"
                "mesen" -> "Mesen"
                "snes9x" -> "Snes9x"
                "parallel_n64" -> "ParaLLEl N64"
                "mupen64plus_next" -> "Mupen64Plus Next"
                "gambatte" -> "Gambatte"
                "libmgba", "mgba" -> "mGBA"
                "pcsx_rearmed" -> "PCSX ReARMed"
                "ppsspp" -> "PPSSPP"
                "genesis_plus_gx" -> "Genesis Plus GX"
                "picodrive" -> "PicoDrive"
                else -> coreName.uppercase().replace("_", " ")
            }
        }
        
    }
    
    private lateinit var retroView: GLRetroView
    private lateinit var console: String
    private lateinit var romPath: String
    private lateinit var gameName: String
    private lateinit var prefs: SharedPreferences
    private lateinit var cheatApplier: com.retroplay.cheat.CheatApplier
    private var currentCoreFilePath: String? = null
    private var gameCRC: String? = null  // Database CRC (if available)
    private var loadedCheats = mutableListOf<com.retroplay.cheat.CheatManager.Cheat>()  // Cheats loaded for current game
    
    // Zapper support (NES light gun)
    private var isZapperGame: Boolean = false
    
    // États des menus
    private val showMainMenu = mutableStateOf(false)
    private val showGamePadSettings = mutableStateOf(false)
    private val showAdvancedOverlaySettings = mutableStateOf(false)
    private val showQuickMenu = mutableStateOf(false)
    private val overlaysVisible = mutableStateOf(true)
    private val showCoreErrorDialog = mutableStateOf(false)
    private val showCoreSelectorFromError = mutableStateOf(false)
    private var failedCoreName = ""
    private val showCoreChangeConfirmDialog = mutableStateOf(false)
    private var coreChangeConfirmMessage = ""
    
    // États pour DIP Switches, Core Options, Game Info et Cheats
    private val showDipSwitchDialog = mutableStateOf(false)
    private val showCoreOptionsDialog = mutableStateOf(false)
    private val showGameInfoDialog = mutableStateOf(false)
    private val showCheatsDialog = mutableStateOf(false)
    private val showSmartConfigDialog = mutableStateOf(false)
    private val showPerGameConfigDialog = mutableStateOf(false)
    private val showDiskSwapperDialog = mutableStateOf(false)
    private var perGameConfigCRC: String? = null
    private var perGameConfigGameName: String = ""
    private var allCoreVariables = mutableStateListOf<CoreVariable>()
    private val dipSwitches = mutableStateListOf<CoreVariable>()
    private val coreOptions = mutableStateListOf<CoreVariable>()
    private var availableDisks = 0
    private var currentDisk = 0
    
    // File picker pour custom .cfg (initialisé AVANT onCreate avec lateinit)
    private lateinit var pickCustomCfgLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    
    /**
     * Gérer le fichier .cfg sélectionné (LECTURE SEULE - aucune modification)
     * Utilise ContentResolver pour gérer les content:// URIs correctement
     */
    private fun handleCustomCfgSelection(uri: android.net.Uri) {
        try {
            Log.i(TAG, "Overlay .cfg selected: $uri")
            
            // Obtenir le nom du fichier via ContentResolver
            val fileName = getFileNameFromUri(uri) ?: run {
                Toast.makeText(this, "Cannot get file name", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.i(TAG, "File name: $fileName")
            
            // Vérifier que c'est bien un .cfg
            if (!fileName.endsWith(".cfg", ignoreCase = true)) {
                Toast.makeText(this, "Please select a .cfg file", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Lire le contenu du fichier pour détecter le type d'overlay
            val cfgContent = readFileFromUri(uri) ?: run {
                Toast.makeText(this, "Cannot read file content", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Extraire le nom du dossier parent depuis l'URI
            // Ex: primary:RetroPlay-Data/overlays/gamepads/flat/dreamcast.cfg → "flat"
            val overlayName = extractOverlayFolderFromUri(uri) ?: 
                detectOverlayNameFromContent(cfgContent, fileName)
            
            if (overlayName.isEmpty()) {
                Toast.makeText(this, "Cannot detect overlay type from .cfg", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.i(TAG, "Detected overlay name: $overlayName")
            
            // Créer le path custom (overlayName/fileName)
            val customPath = "$overlayName/$fileName"
            
            // Sauvegarder dans la liste des customs browsés
            com.retroplay.overlay.models.OverlayPreferenceManager.saveCustomBrowsed(prefs, console, customPath)
            
            // Sauvegarder aussi comme preference active avec le nom du .cfg custom!
            val pref = com.retroplay.overlay.models.OverlayPreference(
                enabled = true,
                overlayName = overlayName,
                customCfgName = fileName,  // "dreamcast.cfg" pour custom, null pour standard
                landscapeLayout = "landscape-A",
                portraitLayout = "portrait-A",
                autoRotate = true
            )
            com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, pref)
            
            Log.i(TAG, "Saved custom overlay: $customPath for console: $console")
            Toast.makeText(this, "Custom overlay '$overlayName' loaded!", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading overlay .cfg", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Obtenir le nom du fichier depuis un content:// URI
     */
    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        var fileName: String? = null
        
        // Méthode 1: Query via ContentResolver
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        // Méthode 2: Fallback - Extraire depuis l'URI
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }
        
        return fileName
    }
    
    /**
     * Lire le contenu d'un fichier depuis son URI
     */
    private fun readFileFromUri(uri: android.net.Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file from URI", e)
            null
        }
    }
    
    /**
     * Apply per-game configuration overrides (if exists).
     * This function loads and applies game-specific settings like Run-Ahead, Rewind, etc.
     * 
     * NOTE: Currently only logs the config as Run-Ahead/Rewind APIs are not yet exposed
     * from LibretroDroid native layer. Full implementation will be added when APIs are ready.
     * 
     * @param gameCRC CRC32 of the game ROM (may be null if not calculated)
     */
    private fun applyPerGameConfig(gameCRC: String?) {
        if (gameCRC == null) {
            Log.d(TAG, "[Config] No gameCRC provided, using global config")
            return
        }
        
        // Load effective config (global + per-game merged)
        val config = com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(gameCRC)
        val hasOverride = com.retroplay.config.RetroPlayConfigManager.hasGameConfig(gameCRC)
        
        if (hasOverride) {
            Log.i(TAG, "[Config] ✅ Per-game config loaded for CRC: $gameCRC")
            Log.i(TAG, "[Config]   Run-Ahead: ${if (config.runAheadEnabled) "${config.runAheadFrames} frames" else "Disabled"}")
            Log.i(TAG, "[Config]   Rewind: ${if (config.rewindEnable) "${config.rewindBufferSize / (1024 * 1024)}MB" else "Disabled"}")
            Log.i(TAG, "[Config]   Fast Forward: ${config.fastforwardRatio}x")
            Log.i(TAG, "[Config]   VSync: ${if (config.videoVsync) "ON" else "OFF"}")
        } else {
            Log.d(TAG, "[Config] Using global config for CRC: $gameCRC")
        }
        
        // TODO: Apply config to emulator once APIs are available
        // if (config.runAheadEnabled) {
        //     retroView.setRunAheadFrames(config.runAheadFrames)
        //     retroView.setRunAheadEnabled(true)
        // }
        // if (config.rewindEnable) {
        //     retroView.setRewindEnabled(true)
        //     retroView.setRewindBufferSize(config.rewindBufferSize)
        // }
        // retroView.setFastForwardRatio(config.fastforwardRatio)
        // retroView.setVsyncEnabled(config.videoVsync)
        
        // For now, we just store the config for future use
        // The config will be accessible when Run-Ahead/Rewind are implemented
    }
    
    /**
     * Extraire le nom du dossier overlay depuis l'URI
     * Ex: content://.../primary:RetroPlay-Data/overlays/gamepads/flat/nes.cfg → "flat"
     */
    private fun extractOverlayFolderFromUri(uri: android.net.Uri): String? {
        return try {
            // DECODE %2F → / pour gérer les URIs encodés!
            val uriString = java.net.URLDecoder.decode(uri.toString(), "UTF-8")
            Log.d(TAG, "Extracting overlay folder from URI: $uriString")
            
            // Chercher pattern /overlays/gamepads/XXX/ ou /overlays/keyboards/XXX/
            val gamepadPattern = Regex("""/overlays/gamepads/([^/]+)/""")
            val keyboardPattern = Regex("""/overlays/keyboards/([^/]+)/""")
            
            val gamepadMatch = gamepadPattern.find(uriString)
            if (gamepadMatch != null) {
                val folder = gamepadMatch.groupValues[1]
                Log.i(TAG, "Extracted overlay folder from URI (gamepads): $folder")
                return folder
            }
            
            val keyboardMatch = keyboardPattern.find(uriString)
            if (keyboardMatch != null) {
                val folder = keyboardMatch.groupValues[1]
                Log.i(TAG, "Extracted overlay folder from URI (keyboards): $folder")
                return folder
            }
            
            Log.w(TAG, "No overlay folder pattern found in URI")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting overlay folder from URI", e)
            null
        }
    }
    
    /**
     * Détecter le nom de l'overlay depuis le contenu du .cfg
     * IMPORTANT: Le overlay name est le NOM DU DOSSIER, pas le nom du fichier!
     * Ex: flat/dreamcast.cfg → overlayName = "flat" (pas "dreamcast")
     */
    private fun detectOverlayNameFromContent(cfgContent: String, fileName: String): String {
        val baseFileName = fileName.substringBeforeLast(".cfg")
        
        // Méthode 1: Parser le contenu pour trouver le path des images
        // Ex: overlay0_desc0_overlay = img/A.png → overlay dans même dossier (utiliser fileName)
        // Ex: overlay0_desc0_overlay = ../flat/img/A.png → overlay = "flat"
        // Ex: overlay0_desc0_overlay = dreamcast/img/A.png → overlay = "dreamcast"
        val imgPathPattern = Regex("""overlay\d+_desc\d+_overlay\s*=\s*["']?([^"'\r\n]+)""")
        val imgMatch = imgPathPattern.find(cfgContent)
        
        if (imgMatch != null) {
            val imgPath = imgMatch.groupValues[1].trim()
            Log.d(TAG, "Found image path in .cfg: $imgPath")
            
            // Cas 1: Path commence par "../" (remonte d'un dossier)
            // Ex: ../flat/img/A.png → overlay = "flat"
            if (imgPath.startsWith("../")) {
                val overlayName = imgPath.removePrefix("../").substringBefore("/")
                Log.i(TAG, "Detected overlay from ../ path: $overlayName")
                return overlayName
            }
            
            // Cas 2: Path contient un dossier parent (mais pas img/)
            // Ex: dreamcast/img/A.png → overlay = "dreamcast"
            // Ex: flat/img/A.png → overlay = "flat"
            if (imgPath.contains("/") && !imgPath.startsWith("img/")) {
                val overlayName = imgPath.substringBefore("/")
                Log.i(TAG, "Detected overlay from image path: $overlayName")
                return overlayName
            }
            
            // Cas 3: Path direct = img/A.png
            // → Overlay name = nom du fichier .cfg
            // Ex: flat.cfg avec img/A.png → overlay = "flat"
            if (imgPath.startsWith("img/")) {
                Log.i(TAG, "Direct img/ path, using fileName as overlay: $baseFileName")
                return baseFileName
            }
        }
        
        // Méthode 2: Chercher la ligne overlay_name (si elle existe)
        // Ex: overlay0_name = "landscape" indique que c'est un multi-layout
        val overlayNamePattern = Regex("""overlay\d+_name\s*=\s*["']([^"']+)["']""")
        val nameMatch = overlayNamePattern.find(cfgContent)
        if (nameMatch != null) {
            Log.d(TAG, "Found overlay0_name in .cfg, using fileName as overlay: $baseFileName")
            return baseFileName
        }
        
        // Méthode 3: Liste des overlays "dossiers" connus
        // Si le fileName correspond à un overlay qui a son propre dossier, l'utiliser
        val knownFolderOverlays = listOf(
            "flat", "dual-shock", "arcade-anim", "lite", "neo-retropad",
            "nes", "nes-small", "snes", "psx", "gba", "n64", "genesis",
            "arcade", "gameboy", "quadpad", "scummvm", "retropad",
            "720-med", "flip_phone", "gb_anim_portrait", "gba-grey"
        )
        
        if (knownFolderOverlays.any { it.equals(baseFileName, ignoreCase = true) }) {
            Log.i(TAG, "FileName matches known folder overlay: $baseFileName")
            return baseFileName
        }
        
        // Méthode 4 (Fallback): Utiliser le nom du fichier
        Log.w(TAG, "Could not detect overlay from content, using fileName: $baseFileName")
        return baseFileName
    }
    
    /**
     * Configure le Zapper (Port 2) manuellement pendant le jeu (À CHAUD)
     * TEST: Reproduire ce que RetroArch fait quand on configure via Quick Menu
     * 
     * RetroArch fait ça à chaud sans redémarrer, donc on fait pareil!
     */
    private fun configureZapperManually() {
        try {
            Log.i(TAG, "[ZAPPER] Manual configuration triggered (hot config)!")
            
            // Configurer Port 1 (index 0) = Gamepad explicitement
            retroView.setControllerType(0, 1)  // RETRO_DEVICE_JOYPAD = 1
            Log.i(TAG, "[ZAPPER] Port 1 configured as GAMEPAD (1)")
            
            // Configurer Port 2 (index 1) = Zapper
            retroView.setControllerType(1, 258)  // RETRO_DEVICE_ZAPPER = 258
            Log.i(TAG, "[ZAPPER] Port 2 configured as ZAPPER (258)")
            
            // Afficher confirmation
            android.widget.Toast.makeText(
                this,
                "Zapper configured! Port 1=Gamepad, Port 2=Zapper (hot config)",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "[ZAPPER] Error during manual configuration", e)
            android.widget.Toast.makeText(
                this,
                "Error: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Termine l'activité de manière sécurisée.
     * MAME2010 a un bug dans son destructeur, on doit donc utiliser killProcess().
     * @param delayMs Délai en millisecondes avant de terminer (pour laisser les dialogs s'afficher)
     */
    private fun safeFinishActivity(currentCore: String?, delayMs: Long = 0) {
        val isMame2010 = currentCore?.contains("mame2010", ignoreCase = true) == true
        
        if (delayMs > 0) {
            // Retarder la fermeture pour laisser le temps aux dialogs de s'afficher
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isMame2010) {
                    Log.w(TAG, "⚠️ MAME2010 detected - using process kill to avoid destructor crash")
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else {
                    Log.i(TAG, "✓ Normal activity finish (returning to GameDetails)")
                    finish() // Retourne à GameDetailsActivity
                }
            }, delayMs)
        } else {
            if (isMame2010) {
                Log.w(TAG, "⚠️ MAME2010 detected - using process kill to avoid destructor crash")
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                Log.i(TAG, "✓ Normal activity finish (returning to GameDetails)")
                finish() // Retourne à GameDetailsActivity
            }
        }
    }
    
    /**
     * Envoie un signal de trigger lightgun au core
     * Simule un appui/relâchement rapide du bouton A sur le port lightgun
     * 
     * @param port Port du lightgun (index 0-3)
     * @param delayMs Délai avant déclenchement (0 = immédiat)
     */
    private fun sendLightgunTrigger(port: Int, delayMs: Int) {
        val sendTrigger = Runnable {
            // Envoyer un pulse rapide de BUTTON_A (DOWN puis UP)
            retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A, port)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_A, port)
            }, 50)  // 50ms pulse
            
            Log.d(TAG, "[ZAPPER] Trigger FIRED on port ${port+1} (delay: ${delayMs}ms)")
        }
        
        if (delayMs > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(sendTrigger, delayMs.toLong())
        } else {
            sendTrigger.run()
        }
    }
    
    /**
     * Gère les actions multi-touch configurables (2/3/4 doigts)
     * 
     * @param event Touch event contenant le nombre de doigts
     */
    private fun handleMultiTouchActions(event: android.view.MotionEvent) {
        val lightgunSettings = com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console)
        val fingerCount = event.pointerCount
        
        if (fingerCount > 1) {
            val actionId = when (fingerCount) {
                2 -> lightgunSettings.lightgunTwoTouchInput
                3 -> lightgunSettings.lightgunThreeTouchInput
                4 -> lightgunSettings.lightgunFourTouchInput
                else -> 0
            }
            
            if (actionId > 0) {
                sendLightgunAction(actionId, lightgunSettings.lightgunPort)
                Log.d(TAG, "[ZAPPER] Multi-touch: $fingerCount fingers → action $actionId")
            }
        }
    }
    
    /**
     * Envoie une action lightgun configurée (multi-touch)
     * Mapping selon RetroArch overlay system
     * 
     * @param actionId ID de l'action (1=START, 2=SELECT, 3=AUX_A, etc.)
     * @param port Port du lightgun
     */
    private fun sendLightgunAction(actionId: Int, port: Int) {
        val keyCode = when (actionId) {
            1 -> android.view.KeyEvent.KEYCODE_BUTTON_START   // LIGHTGUN_START
            2 -> android.view.KeyEvent.KEYCODE_BUTTON_SELECT  // LIGHTGUN_SELECT
            3 -> android.view.KeyEvent.KEYCODE_BUTTON_A       // LIGHTGUN_AUX_A
            4 -> android.view.KeyEvent.KEYCODE_BUTTON_B       // LIGHTGUN_AUX_B
            5 -> android.view.KeyEvent.KEYCODE_BUTTON_X       // LIGHTGUN_AUX_C
            6 -> android.view.KeyEvent.KEYCODE_DPAD_UP        // LIGHTGUN_DPAD_UP
            7 -> android.view.KeyEvent.KEYCODE_DPAD_DOWN      // LIGHTGUN_DPAD_DOWN
            8 -> android.view.KeyEvent.KEYCODE_DPAD_LEFT      // LIGHTGUN_DPAD_LEFT
            9 -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT     // LIGHTGUN_DPAD_RIGHT
            else -> return  // 0 = none
        }
        
        // Envoyer pulse rapide
        retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode, port)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, keyCode, port)
        }, 50)
        
        Log.d(TAG, "[ZAPPER] Multi-touch action $actionId sent: keyCode=$keyCode on port ${port+1}")
    }
    
    /**
     * Gestion des touches Zapper - Envoie position POINTER + trigger au port 2
     * Port 1 (index 0) = Manette standard (Start/Select pour menus)
     * Port 2 (index 1) = Zapper (RETRO_DEVICE_POINTER configuré)
     * 
     * Utilise RETRO_DEVICE_POINTER (6) pour envoyer coordonnées exactes au core FCEUmm
     * 
     * @param event Touch event
     * @param gameViewBounds Bounds exacts du GLRetroView (zone de jeu)
     * @param triggerOnTouch Si true, tir instantané au DOWN, sinon au UP
     * @param allowOffscreen Si false, clamp position aux bounds
     * @param triggerDelay Délai en ms avant déclenchement du trigger
     * @param lightgunPort Port du lightgun (0-3, -1 = tous)
     */
    private fun handleZapperTouch(
        event: android.view.MotionEvent,
        gameViewBounds: androidx.compose.ui.geometry.Rect?,
        triggerOnTouch: Boolean = true,
        allowOffscreen: Boolean = true,
        triggerDelay: Int = 0,
        lightgunPort: Int = 1  // Port 2 (index 1) = Zapper NES traditionnel
    ): Boolean {
        if (!isZapperGame) {
            return false
        }
        
        // Vérifier si bounds disponibles
        val bounds = gameViewBounds
        if (bounds == null) {
            Log.w(TAG, "[ZAPPER] GLRetroView bounds not available yet, ignoring touch")
            return false
        }
        
        // Touch coordinates (écran)
        val touchX = event.x
        val touchY = event.y
        
        // Vérifier si touch est DANS le GLRetroView (zone de jeu)
        val isInGameArea = touchX >= bounds.left && touchX <= bounds.right &&
                          touchY >= bounds.top && touchY <= bounds.bottom
        
        if (!isInGameArea) {
            // Touch hors zone de jeu (dans les overlays, bars, etc.)
            if (!allowOffscreen) {
                Log.d(TAG, "[ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored")
                return false
            }
            // Si allowOffscreen=true, clamp aux bounds
            Log.d(TAG, "[ZAPPER] Touch OUTSIDE game area, clamping to bounds")
        }
        
        // CRITIQUE FIX: Les bounds incluent l'offset vertical (QuickActionsBar + portrait offset)
        // Mais le touch est en coordonnées ÉCRAN absolues!
        // Il faut ajuster pour que le milieu de l'écran visible = milieu du jeu NES
        
        val viewport = retroView.viewport  // RectF(left, top, right, bottom) normalisé [0-1]
        
        // CORRECTION PORTRAIT: Convertir touchY (coordonnées ÉCRAN) en coordonnées VIEW
        // bounds.top peut être négatif (View déborde en haut de l'écran)
        // Exemple: bounds.top=-537, bounds.bottom=1803, touchY=1170 (centre écran)
        // touchYInView = 1170 - (-537) = 1707 (coordonnées dans le View)
        val touchXInView = touchX - bounds.left
        val touchYInView = touchY - bounds.top
        
        // CORRECTION VIEWPORT: LibretroDroid retourne (0,0,1,1) même avec letterboxing!
        // Récupérer le VRAI ratio d'aspect depuis le core (au lieu de deviner)
        val coreAspectRatio = try {
            retroView.getAspectRatio()
        } catch (e: Exception) {
            Log.w(TAG, "[ZAPPER] Cannot get aspect ratio from core, using NES default (256:240)")
            256f / 240f  // Fallback NES
        }
        
        // Récupérer les dimensions de rendu du core (pour debug)
        val gameWidth = try { retroView.getGameGeometryWidth() } catch (e: Exception) { 256 }
        val gameHeight = try { retroView.getGameGeometryHeight() } catch (e: Exception) { 240 }
        
        val screenAspectRatio = bounds.width / bounds.height
        
        val actualViewport = if (screenAspectRatio > coreAspectRatio) {
            // Écran plus large que le jeu → Bandes noires à gauche/droite
            val gameWidth = bounds.height * coreAspectRatio
            val letterboxWidth = (bounds.width - gameWidth) / 2f
            val left = letterboxWidth / bounds.width
            val right = 1f - left
            android.graphics.RectF(left, 0f, right, 1f)
        } else {
            // Écran plus haut que le jeu → Bandes noires en haut/bas (portrait typique)
            val gameHeight = bounds.width / coreAspectRatio
            val letterboxHeight = (bounds.height - gameHeight) / 2f
            val top = letterboxHeight / bounds.height
            val bottom = 1f - top
            android.graphics.RectF(0f, top, 1f, bottom)
        }
        
        // Appliquer le viewport CORRIGÉ (si letterboxing)
        val viewportTop = actualViewport.top * bounds.height
        val viewportBottom = actualViewport.bottom * bounds.height
        val viewportLeft = actualViewport.left * bounds.width
        val viewportRight = actualViewport.right * bounds.width
        
        val clampedX = touchXInView.coerceIn(viewportLeft, viewportRight)
        val clampedY = touchYInView.coerceIn(viewportTop, viewportBottom)
        
        val viewportWidth = viewportRight - viewportLeft
        val viewportHeight = viewportBottom - viewportTop
        
        val relativeX = (clampedX - viewportLeft) / viewportWidth
        val relativeY = (clampedY - viewportTop) / viewportHeight
        
        // LibretroDroid ATTEND [0, 1] et fait la conversion [-0x7fff, +0x7fff] lui-même !
        // Formule dans input.cpp: (pointerScreenXAxis - 0.5f) * 2.0 * 0x7fff
        // POINTER_PRESSED = (X >= 0 && Y >= 0) donc on DOIT envoyer [0, 1] !
        
        // CALCULS DÉTAILLÉS pour debug (simulation des conversions)
        // LibretroDroid: result = 2.0 * (relativeY - 0.5f) * 32767
        val libretroX = ((relativeX - 0.5f) * 2.0f * 32767f).toInt()  // Conversion LibretroDroid
        val libretroY = ((relativeY - 0.5f) * 2.0f * 32767f).toInt()
        
        // Conversion FCEUmm (simulation de libretro.c ligne 2442-2443, 2454-2455)
        // Lire les valeurs de crop overscan depuis le .cfg
        val config = if (console == "nes") {
            CoreConfigManager.loadConfig(this, "FCEUmm")
        } else {
            emptyMap()
        }
        val cropTop = config["fceumm_overscan_v_top"]?.toIntOrNull() ?: 8
        val cropLeft = config["fceumm_overscan_h_left"]?.toIntOrNull() ?: 0
        
        val fceummOffsetX = (cropLeft * 0x120) - 1  // Ex: (0 * 0x120) - 1 = -1
        val fceummOffsetY = (cropTop * 0x133) + 1   // Ex: (8 * 0x133) + 1 = 2457
        
        // max_width et max_height dans FCEUmm = dimensions APRÈS crop
        val maxWidth = gameWidth   // 256 (pas de crop horizontal)
        val maxHeight = gameHeight // 224 (avec crop 8+8) ou 240 (sans crop)
        
        val fceummX = ((libretroX + (32767 + fceummOffsetX)) * maxWidth) / ((32767 + fceummOffsetX) * 2)
        val fceummY = ((libretroY + (32767 + fceummOffsetY)) * maxHeight) / ((32767 + fceummOffsetY) * 2)
        
        // DEBUG: Calculer où FCEUmm va dessiner le crosshair (NES coords 0-255, 0-239)
        // Le crosshair FCEUmm est dessiné à FCEU_DrawGunSight(buf, mousedata[0], mousedata[1])
        // Donc si crosshair est "trop bas" → fceummY est trop grand → relativeY trop grand
        
        // CONVERSION INVERSE: Où FCEUmm pense qu'on vise en pixels écran (pour debug)
        // fceummX/Y sont en coordonnées NES (0-255, 0-239)
        // max_height dans FCEUmm = gameHeight (224 avec crop, 240 sans crop)
        // Pour convertir en pixels viewport: fceummY / gameHeight * viewportHeight + viewportTop
        val fceummCrosshairXInViewport = (fceummX / gameWidth.toFloat()) * viewportWidth + viewportLeft
        val fceummCrosshairYInViewport = (fceummY / gameHeight.toFloat()) * viewportHeight + viewportTop
        val fceummCrosshairXOnScreen = fceummCrosshairXInViewport + bounds.left
        val fceummCrosshairYOnScreen = fceummCrosshairYInViewport + bounds.top
        
        // Calculer l'écart entre notre touch et où FCEUmm pense qu'on vise
        val deltaX = touchX - fceummCrosshairXOnScreen
        val deltaY = touchY - fceummCrosshairYOnScreen
        
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                // Envoyer position POINTER au core (pour coordonnées X/Y)
                retroView.sendMotionEvent(
                    com.swordfish.libretrodroid.LibretroDroid.MOTION_SOURCE_POINTER,
                    relativeX,  // 0.0 à 1.0 (LibretroDroid convertit)
                    relativeY,  // 0.0 à 1.0
                    lightgunPort  // Port configuré (index 0-3)
                )
                
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    // Déclencher le trigger selon l'option triggerOnTouch
                    if (triggerOnTouch) {
                        // NOUVEAU: Envoyer MOUSE BUTTON LEFT (trigger)
                        // RetroArch Android mappe le trigger Zapper à Mouse Button 1 (clic gauche)
                        retroView.sendMouseButton(
                            com.swordfish.libretrodroid.LibretroDroid.MOUSE_BUTTON_LEFT,
                            true,  // Pressed
                            lightgunPort
                        )
                        Log.i(TAG, "[ZAPPER] MOUSE BUTTON LEFT pressed on port $lightgunPort (triggerOnTouch=true)")
                    } else {
                        Log.i(TAG, "[ZAPPER] Touch DOWN registered, waiting for UP to trigger (triggerOnTouch=false)")
                    }
                    
                    Log.d(TAG, "[ZAPPER CONVERSIONS]")
                    Log.d(TAG, "  1. Touch écran (raw):      (${touchX.toInt()}, ${touchY.toInt()})")
                    Log.d(TAG, "  2. GLRetroView bounds:     left=${bounds.left.toInt()}, top=${bounds.top.toInt()}, right=${bounds.right.toInt()}, bottom=${bounds.bottom.toInt()}")
                    Log.d(TAG, "  3. Touch in View coords:   (${touchXInView.toInt()}, ${touchYInView.toInt()})")
                    Log.d(TAG, "  4. Game Geometry:          ${gameWidth}x${gameHeight} (ratio: $coreAspectRatio, crop: top=$cropTop bottom=${config["fceumm_overscan_v_bottom"] ?: "8"})")
                    Log.d(TAG, "  5. Viewport LibretroDroid: left=${viewport.left}, top=${viewport.top}, right=${viewport.right}, bottom=${viewport.bottom}")
                    Log.d(TAG, "  6. Viewport CORRECTED:     left=${actualViewport.left}, top=${actualViewport.top}, right=${actualViewport.right}, bottom=${actualViewport.bottom}")
                    Log.d(TAG, "  7. Viewport pixels (View): left=${viewportLeft.toInt()}, top=${viewportTop.toInt()}, right=${viewportRight.toInt()}, bottom=${viewportBottom.toInt()}")
                    Log.d(TAG, "  8. Touch dans viewport?    $isInGameArea")
                    Log.d(TAG, "  9. Clamped to viewport:    (${clampedX.toInt()}, ${clampedY.toInt()})")
                    Log.d(TAG, "  10. Relative [0-1]:        ($relativeX, $relativeY)")
                    Log.d(TAG, "  11. Libretro int16:        ($libretroX, $libretroY)")
                    Log.d(TAG, "  12. FCEUmm offsets:        X=$fceummOffsetX Y=$fceummOffsetY")
                    Log.d(TAG, "  13. FCEUmm NES [0-${maxWidth-1}]x[0-${maxHeight-1}]: ($fceummX, $fceummY)")
                    val nesValid = fceummX in 0 until maxWidth && fceummY in 0 until maxHeight
                    Log.d(TAG, "  14. NES coords valid?      $nesValid")
                    Log.d(TAG, "  15. FCEUmm crosshair (screen): (${fceummCrosshairXOnScreen.toInt()}, ${fceummCrosshairYOnScreen.toInt()})")
                    Log.d(TAG, "  16. Delta (touch - FCEUmm): (${deltaX.toInt()}px, ${deltaY.toInt()}px) ${if (deltaY > 0) "FCEUmm trop BAS" else if (deltaY < 0) "FCEUmm trop HAUT" else "ALIGNÉ"}")
                    Log.d(TAG, "  17. Port: $lightgunPort | PRESSED: ${relativeX >= 0f && relativeY >= 0f}")
                    
                    // NOTE: En mode RetroPointer, le trigger est AUTOMATIQUE via POINTER_PRESSED
                    // FCEUmm lit: input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED)
                    // LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0) ? 1 : 0
                    // Donc pas besoin d'envoyer BUTTON_A! Le trigger est automatique!
                    
                    // Gérer multi-touch (2/3/4 doigts) pour actions START/SELECT/etc.
                    handleMultiTouchActions(event)
                }
                return true
            }
            
            android.view.MotionEvent.ACTION_UP -> {
                // Si triggerOnTouch = false, déclencher le trigger MAINTENANT (au release)
                if (!triggerOnTouch) {
                    retroView.sendMouseButton(
                        com.swordfish.libretrodroid.LibretroDroid.MOUSE_BUTTON_LEFT,
                        true,  // Pressed
                        lightgunPort
                    )
                    Log.i(TAG, "[ZAPPER] MOUSE BUTTON LEFT pressed on port $lightgunPort (triggerOnTouch=false, firing on UP)")
                    
                    // Attendre triggerDelay si configuré
                    if (triggerDelay > 0) {
                        Thread.sleep(triggerDelay.toLong())
                    }
                }
                
                // CRITIQUE: Envoyer coordonnées NÉGATIVES pour forcer POINTER_PRESSED = 0
                // LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0) ? 1 : 0
                // Si on ne fait pas ça, les coordonnées restent en mémoire et POINTER_PRESSED reste à 1!
                retroView.sendMotionEvent(
                    com.swordfish.libretrodroid.LibretroDroid.MOTION_SOURCE_POINTER,
                    -1f,  // X négatif → pointerScreenXAxis < 0
                    -1f,  // Y négatif → pointerScreenYAxis < 0
                    lightgunPort
                )
                Log.d(TAG, "[ZAPPER] Touch UP - POINTER reset to (-1, -1), POINTER_PRESSED now FALSE")
                
                // Release MOUSE BUTTON LEFT
                retroView.sendMouseButton(
                    com.swordfish.libretrodroid.LibretroDroid.MOUSE_BUTTON_LEFT,
                    false,  // Released
                    lightgunPort
                )
                Log.i(TAG, "[ZAPPER] MOUSE BUTTON LEFT released on port $lightgunPort")
                
                return true
            }
            
            else -> return false
        }
    }
    
    /**
     * Charge les variables de core (DIP switches et Core Options) et applique les valeurs sauvegardées
     */
    private fun loadCoreVariables() {
        try {
            // Extraire le coreId AVANT de récupérer les variables pour vérifier la cohérence
            val expectedCoreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
            Log.i(TAG, "Loading variables for core: $expectedCoreId")
            
            // Récupérer les variables depuis le core LibretroDroid
            val variables = retroView.getVariables()
            Log.i(TAG, "Retrieved ${variables.size} variables from core")
            
            // Vérifier que les variables correspondent au core attendu
            if (variables.isNotEmpty()) {
                val firstKey = variables.firstOrNull()?.key ?: ""
                Log.i(TAG, "First variable key: $firstKey (expected prefix: $expectedCoreId)")
            }
            
            // Parser les variables
            val parsed = CoreVariableManager.parseVariables(variables)
            
            // Filtrer pour ne garder que les variables du core actuel
            val filtered = CoreVariableManager.filterVariablesForCore(parsed, expectedCoreId)
            
            allCoreVariables.clear()
            allCoreVariables.addAll(filtered)
            
            // Séparer DIP switches et Core Options
            dipSwitches.clear()
            dipSwitches.addAll(CoreVariableManager.getDipSwitches(filtered))
            coreOptions.clear()
            coreOptions.addAll(CoreVariableManager.getCoreOptions(filtered))
            
            Log.i(TAG, "Parsed: ${dipSwitches.size} DIP switches, ${coreOptions.size} core options")
            
            // Extraire le coreId depuis le chemin du fichier
            val coreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
            val gameId = File(romPath).nameWithoutExtension
            
            // Charger les valeurs sauvegardées
            val savedValues = CoreVariableManager.loadVariables(this, gameId, coreId)
            
            if (savedValues.isNotEmpty()) {
                // Appliquer les valeurs sauvegardées
                val updatedVariables = CoreVariableManager.applyLoadedValues(filtered, savedValues)
                allCoreVariables.clear()
                allCoreVariables.addAll(updatedVariables)
                dipSwitches.clear()
                dipSwitches.addAll(CoreVariableManager.getDipSwitches(updatedVariables))
                coreOptions.clear()
                coreOptions.addAll(CoreVariableManager.getCoreOptions(updatedVariables))
                
                // Appliquer au core LibretroDroid
                val libretroVars = CoreVariableManager.toLibretroVariables(updatedVariables)
                retroView.updateVariables(*libretroVars)
                Log.i(TAG, "Applied ${savedValues.size} saved variables to core")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading core variables", e)
        }
    }
    
    /**
     * Take a screenshot of the current game
     */
    private fun takeScreenshot() {
        lifecycleScope.launch {
            try {
                var screenshotBitmap: android.graphics.Bitmap? = null
                
                // Capture screenshot from GL thread
                retroView.queueEvent {
                    try {
                        val width = retroView.width
                        val height = retroView.height
                        screenshotBitmap = ScreenshotManager.captureScreenshotGL(width, height)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to capture screenshot from GL", e)
                    }
                }
                
                // Wait a bit for GL thread to finish
                kotlinx.coroutines.delay(100)
                
                screenshotBitmap?.let { bitmap ->
                    // Save screenshot
                    val path = ScreenshotManager.saveScreenshot(bitmap, console, gameName)
                    
                    // Also save/update thumbnail if we have CRC
                    gameCRC?.let { crc ->
                        ScreenshotManager.saveThumbnail(bitmap, crc, console)
                    }
                    
                    runOnUiThread {
                        if (path != null) {
                            Toast.makeText(this@RetroArchEmulatorActivity, 
                                "Screenshot saved", Toast.LENGTH_SHORT).show()
                            Log.i(TAG, "Screenshot saved: $path")
                        } else {
                            Toast.makeText(this@RetroArchEmulatorActivity, 
                                "Failed to save screenshot", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    runOnUiThread {
                        Toast.makeText(this@RetroArchEmulatorActivity, 
                            "Failed to capture screenshot", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot error", e)
                runOnUiThread {
                    Toast.makeText(this@RetroArchEmulatorActivity, 
                        "Screenshot error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupFullscreenMode()
        
        romPath = intent.getStringExtra("romPath") ?: run {
            Log.e(TAG, "No ROM path provided")
            Toast.makeText(this, "Error: No ROM path", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        console = intent.getStringExtra("console") ?: "psx"
        gameName = intent.getStringExtra("gameName") ?: "Game"
        gameCRC = intent.getStringExtra("gameCRC")  // Database CRC (may be null)
        val gameId = intent.getStringExtra("gameId") ?: gameName  // Use gameName as fallback
        val loadSlot = intent.getIntExtra("loadSlot", 0)  // 0 = nouvelle partie, 1-5 = charger slot
        
        // Load per-game config (if exists)
        applyPerGameConfig(gameCRC)
        
        // Auto-generate thumbnail if not exists (5 seconds after game start)
        gameCRC?.let { crc ->
            if (!ScreenshotManager.hasThumbnail(crc, console)) {
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(5000) // Wait 5 seconds for game to start
                    var thumbnailBitmap: android.graphics.Bitmap? = null
                    
                    retroView.queueEvent {
                        try {
                            val width = retroView.width
                            val height = retroView.height
                            thumbnailBitmap = ScreenshotManager.captureScreenshotGL(width, height)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to auto-capture thumbnail", e)
                        }
                    }
                    
                    kotlinx.coroutines.delay(100)
                    
                    thumbnailBitmap?.let { bitmap ->
                        ScreenshotManager.saveThumbnail(bitmap, crc, console)
                        Log.i(TAG, "Auto-generated thumbnail for $gameName")
                    }
                }
            }
        }
        
        // Détecter les jeux Zapper AVANT la création de GLRetroViewData
        // pour pouvoir passer les variables initiales au core
        isZapperGame = ZapperGameDetector.isZapperGame(gameName, console)
        if (isZapperGame) {
            Log.i(TAG, "[ZAPPER] Zapper game detected EARLY: $gameName")
            
            // Recommandation mode panoramique pour meilleure précision
            val configuration = resources.configuration
            val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            if (isPortrait) {
                android.widget.Toast.makeText(
                    this,
                    "For better Zapper accuracy, use landscape mode",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        
        Log.i(TAG, "🟢 NativeComposeEmulator starting: $gameName ($console) from $romPath" + 
                if (loadSlot > 0) " [LOAD SLOT $loadSlot]" else " [NEW GAME]")
        
        // === DÉTECTION DE CRASH (SANS FALLBACK AUTOMATIQUE) ===
        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        val lastGamePath = crashPrefs.getString(KEY_LAST_GAME, null)
        val lastCoreAttempted = crashPrefs.getString(KEY_LAST_CORE, null)
        val lastTimestamp = crashPrefs.getLong(KEY_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        
        // Charger les SharedPreferences
        prefs = getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)
        
        // Quick Wins: Charger états Fast Forward et Audio Mute
        fastForwardRatio = prefs.getInt("emulation_fast_forward_ratio", 2).coerceIn(1, 4)
        audioMuted.value = prefs.getBoolean("emulation_audio_muted", false)
        
        // Quick Win #4: Charger shader préféré
        val savedShaderName = prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT"
        currentShader.value = com.retroplay.shader.ShaderManager.fromString(savedShaderName)
        
        // QuickActionsBar visibility: Charger état
        quickActionsBarVisible.value = prefs.getBoolean("emulation_quick_actions_bar_visible", true)
        
        // Crosshair Mode: Charger mode d'affichage du crosshair Zapper
        val savedCrosshairMode = prefs.getString("emulation_crosshair_mode", "RETROPLAY_ONLY") ?: "RETROPLAY_ONLY"
        crosshairMode.value = try {
            CrosshairMode.valueOf(savedCrosshairMode)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid crosshair mode: $savedCrosshairMode, using default")
            CrosshairMode.RETROPLAY_ONLY
        }
        
        // Initialiser le file picker AVANT setContent (CRITIQUE pour lifecycle)
        pickCustomCfgLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                handleCustomCfgSelection(uri)
            } else {
                Log.w(TAG, "No file selected")
            }
        }
        
        // ALWAYS use RetroArch overlays (this activity is dedicated to RetroArch mode only)
        val savedVariant = GamePadLayoutManager.LayoutVariant.RETROARCH
        Log.i(TAG, "⚙️ RetroArch mode FORCED (pure RetroArch overlays - NO Radial gamepads)")
        
        // Créer GLRetroView avec GLRetroViewData
        val data = com.swordfish.libretrodroid.GLRetroViewData(this).apply {
            // Utiliser le core normal (avec override si défini)
            val selectedCore = getCorePath(console)
            
            // Vérifier si le dernier lancement a crashé (même jeu, moins de 5s)
            val wasCrash = lastGamePath == romPath && 
                           lastCoreAttempted != null && 
                           (currentTime - lastTimestamp) < CRASH_TIMEOUT_MS
            
            // N'afficher le dialog QUE si le core est IDENTIQUE au core qui a crashé
            // Si l'utilisateur a changé le core manuellement, ne pas afficher le dialog
            if (wasCrash && lastCoreAttempted == selectedCore) {
                Log.w(TAG, "⚠️ CRASH DETECTED on previous attempt with core: $lastCoreAttempted")
                // Extraire le nom du core pour l'affichage (nom lisible)
                failedCoreName = getCoreDisplayName(lastCoreAttempted)
                // Nettoyer les prefs pour ne pas re-afficher le dialog
                crashPrefs.edit().clear().apply()
                // Afficher le dialog d'erreur
                showCoreErrorDialog.value = true
            } else if (wasCrash && lastCoreAttempted != selectedCore) {
                // L'utilisateur a déjà changé le core manuellement, juste nettoyer les prefs
                Log.i(TAG, "✓ User manually changed core from $lastCoreAttempted to $selectedCore")
                crashPrefs.edit().clear().apply()
            }
            
            coreFilePath = selectedCore
            gameFilePath = romPath
            
            // Sauvegarder le core actuel pour le cleanup
            this@RetroArchEmulatorActivity.currentCoreFilePath = selectedCore
            
            // Sauvegarder la tentative actuelle pour détecter un crash futur
            crashPrefs.edit().apply {
                putString(KEY_LAST_GAME, romPath)
                putString(KEY_LAST_CORE, selectedCore)
                putLong(KEY_TIMESTAMP, currentTime)
                apply()
            }
            Log.i(TAG, "📝 Core logged: $selectedCore for $gameName")
            
            // System directory (BIOS)
            systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"
            
            // Saves directory (SHARED)
            val sharedSavesDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console")
            if (!sharedSavesDir.exists()) sharedSavesDir.mkdirs()
            savesDirectory = sharedSavesDir.absolutePath
            
            // Shader (appliquer le shader sauvegardé)
            shader = com.retroplay.shader.ShaderManager.getShaderConfig(currentShader.value)
            
            // Options
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true

            // Configuration des variables de core via l'API officielle LibretroDroid
            val corePrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@RetroArchEmulatorActivity)
            val prefix = "${console}_"

            // Détecter le core N64 réellement utilisé
            val actualCoreFile = coreFilePath ?: ""
            val isParallelN64 = actualCoreFile.contains("parallel_n64")
            val isMupen64Plus = actualCoreFile.contains("mupen64plus")

            // Configuration des variables selon la console
            Log.i(TAG, "[INIT] Configuring core variables for console: '$console', isZapperGame=$isZapperGame")
            when (console) {
                "n64" -> {
                    val resolution = corePrefs.getInt("${prefix}n64_resolution", 0)
                    val antialiasing = corePrefs.getInt("${prefix}n64_antialiasing", 0)
                    val bilinear = corePrefs.getBoolean("${prefix}n64_bilinear", false)
                    Log.i(TAG, "[N64] Core options loaded - Resolution: $resolution, AA: $antialiasing, Bilinear: $bilinear")
                    Log.i(TAG, "[N64] Detected core file: $actualCoreFile (Parallel: $isParallelN64, Mupen64Plus: $isMupen64Plus)")

                    // Appliquer les variables selon le core détecté
                    val n64Variables = if (isParallelN64) {
                        // ParaLLEl N64 variables
                        arrayOf(
                            Variable("parallel-n64-screensize", when (resolution) {
                                0 -> "320x240"
                                1 -> "640x480"
                                2 -> "960x720"
                                3 -> "1280x960"
                                else -> "320x240"
                            }),
                            Variable("parallel-n64-antialiasmode", antialiasing.toString()),
                            Variable("parallel-n64-bilinear_mode", if (bilinear) "1" else "0")
                        )
                    } else if (isMupen64Plus) {
                        // Mupen64Plus Next variables (plus limitées que ParaLLEl N64)
                        // Mupen64Plus a moins d'options configurables via variables
                        Log.i(TAG, "[N64] Mupen64Plus Next detected - limited core options available")
                        // Pour l'instant, pas de variables spécifiques connues pour Mupen64Plus
                        // On pourrait ajouter des variables si elles sont découvertes
                        emptyArray<Variable>()
                    } else {
                        // Core inconnu - utiliser les variables ParaLLEl N64 par défaut
                        Log.w(TAG, "[N64] Unknown N64 core, using ParaLLEl N64 variables as fallback")
                        arrayOf(
                            Variable("parallel-n64-screensize", when (resolution) {
                                0 -> "320x240"
                                1 -> "640x480"
                                2 -> "960x720"
                                3 -> "1280x960"
                                else -> "320x240"
                            }),
                            Variable("parallel-n64-antialiasmode", antialiasing.toString()),
                            Variable("parallel-n64-bilinear_mode", if (bilinear) "1" else "0")
                        )
                    }

                    try {
                        variables = n64Variables
                        val coreName = when {
                            isParallelN64 -> "ParaLLEl N64"
                            isMupen64Plus -> "Mupen64Plus Next"
                            else -> "Unknown N64 core"
                        }
                        Log.i(TAG, "[N64] Core variables set for $coreName via GLRetroViewData.variables API")
                    } catch (e: Exception) {
                        Log.w(TAG, "[N64] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "psx", "ps1", "playstation" -> {
                    val resolution = corePrefs.getInt("${prefix}psx_resolution", 0)
                    val textureFiltering = corePrefs.getBoolean("${prefix}psx_texture_filtering", true)
                    val dithering = corePrefs.getBoolean("${prefix}psx_dithering", true)
                    Log.i(TAG, "[PSX] Core options loaded - Resolution: $resolution, Filter: $textureFiltering, Dither: $dithering")

                    // Créer les variables PSX (Array<Variable>)
                    val psxVariables = arrayOf(
                        Variable("beetle_psx_hw_internal_resolution", when (resolution) {
                            0 -> "1"
                            1 -> "2"
                            2 -> "4"
                            3 -> "8"
                            else -> "1"
                        }),
                        Variable("beetle_psx_hw_filter", if (textureFiltering) "1" else "0"),
                        Variable("beetle_psx_hw_dithering", if (dithering) "enabled" else "disabled")
                    )

                    try {
                        variables = psxVariables
                        Log.i(TAG, "[PSX] Core variables set via GLRetroViewData.variables API")
                    } catch (e: Exception) {
                        Log.w(TAG, "[PSX] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "nes" -> {
                    // Configuration NES / FCEUmm depuis .cfg
                    Log.i(TAG, "[NES] Loading FCEUmm configuration from .cfg file")
                    
                    // Créer la config par défaut si inexistante
                    CoreConfigManager.createDefaultConfigIfNeeded(this@RetroArchEmulatorActivity, "FCEUmm", CoreConfigManager.getDefaultConfig("fceumm"))
                    
                    // Charger la config depuis le .cfg
                    val config = CoreConfigManager.loadConfig(this@RetroArchEmulatorActivity, "FCEUmm")
                    
                    if (config.isNotEmpty()) {
                        // Convertir Map<String, String> en Array<Variable>
                        val nesVariables = config.map { (key, value) ->
                            Variable(key, value)
                        }.toTypedArray()
                        
                        try {
                            variables = nesVariables
                            Log.i(TAG, "[NES] Loaded ${nesVariables.size} variables from FCEUmm.cfg")
                            config.forEach { (key, value) ->
                                Log.d(TAG, "[NES]   $key = \"$value\"")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[NES] Failed to set variables from .cfg: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "[NES] No configuration found in FCEUmm.cfg, using defaults")
                    }
                }
                "snes" -> {
                    val blendMode = corePrefs.getInt("${prefix}snes_blend_mode", 0)
                    val hires = corePrefs.getBoolean("${prefix}snes_hires", false)
                    Log.i(TAG, "[SNES] Core options loaded - Blend: $blendMode, HiRes: $hires")

                    // Créer les variables SNES (Array<Variable>)
                    val snesVariables = arrayOf(
                        Variable("snes9x_blend_layers", when (blendMode) {
                            0 -> "disabled"
                            1 -> "merge"
                            2 -> "additive"
                            3 -> "subtractive"
                            else -> "disabled"
                        }),
                        Variable("snes9x_hires_blend", if (hires) "enabled" else "disabled")
                    )

                    try {
                        variables = snesVariables
                        Log.i(TAG, "[SNES] Core variables set via GLRetroViewData.variables API")
                    } catch (e: Exception) {
                        Log.w(TAG, "[SNES] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "arcade", "mame" -> {
                    // MAME cores have issues with variable initialization in LibretroDroid
                    // Using empty array to avoid segfaults during core initialization/destruction
                    Log.i(TAG, "[ARCADE] Initializing MAME core with empty variables array (console=$console)")

                    try {
                        variables = emptyArray()
                        Log.i(TAG, "[ARCADE] MAME core variables initialized (empty array)")
                    } catch (e: Exception) {
                        Log.e(TAG, "[ARCADE] Failed to initialize MAME variables: ${e.message}")
                    }
                }
                "fbneo", "neogeo", "cps1", "cps2" -> {
                    // FBNeo core options
                    Log.i(TAG, "[ARCADE] Setting up FBNeo core options (console=$console)")

                    try {
                        variables = emptyArray()
                        Log.i(TAG, "[ARCADE] FBNeo core variables initialized (empty array)")
                    } catch (e: Exception) {
                        Log.w(TAG, "[ARCADE] Failed to set FBNeo variables: ${e.message}")
                    }
                }
                else -> {
                    // Pour toutes les autres consoles sans configuration spécifique,
                    // initialiser un tableau vide pour éviter les crashes de segfault
                    Log.i(TAG, "[$console] Initializing default core variables (empty array)")
                    
                    try {
                        variables = emptyArray()
                        Log.i(TAG, "[$console] Default core variables initialized")
                    } catch (e: Exception) {
                        Log.w(TAG, "[$console] Failed to set default variables: ${e.message}")
                    }
                }
            }
        }
        
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)
        
        // Quick Wins: Appliquer l'état audio au démarrage (après création de retroView)
        retroView.audioEnabled = !audioMuted.value
        
        // Initialize disk info for multi-disc games (PSX, etc.)
        lifecycleScope.launch {
            try {
                retroView.getGLRetroEvents().collect { event ->
                    if (event is GLRetroView.GLRetroEvents.FrameRendered) {
                        // Refresh disk info periodically (every 60 frames ~1 second)
                        if (System.currentTimeMillis() % 1000 < 17) {
                            availableDisks = retroView.getAvailableDisks()
                            currentDisk = retroView.getCurrentDisk()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting GLRetroEvents for disk info: ${e.message}")
            }
        }
        
        // === ÉCOUTE DES ERREURS LIBRETRODROID (CHARGEMENT ÉCHOUÉ) ===
        lifecycleScope.launch {
            try {
                retroView.getGLRetroErrors().collect { errorCode ->
                    Log.e(TAG, "❌ GLRetroView ERROR detected: $errorCode")
                    
                    // Erreurs qui nécessitent l'affichage du dialog
                    val needsUserAction = errorCode == GLRetroView.ERROR_LOAD_GAME || 
                                         errorCode == GLRetroView.ERROR_LOAD_LIBRARY
                    
                    if (needsUserAction) {
                        val selectedCore = data.coreFilePath ?: "unknown"
                        Log.w(TAG, "⚠️ Core $selectedCore failed to load game")
                        
                        // Extraire le nom du core pour l'affichage (nom lisible)
                        failedCoreName = getCoreDisplayName(selectedCore)
                        
                        // Afficher le dialog d'erreur
                        runOnUiThread {
                            showCoreErrorDialog.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting GLRetroErrors: ${e.message}")
            }
        }
        
        // === DÉTECTION DE SUCCÈS (NETTOYER LES PREFS DE CRASH) ===
        // Si le jeu tourne pendant 5 secondes sans crash, nettoyer les prefs
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                // Vérifier si le dialog d'erreur est affiché
                // Si oui, le jeu n'est PAS chargé correctement, ne pas déclarer le succès
                if (!showCoreErrorDialog.value) {
                    // Succès ! Le jeu ne crashe pas avec ce core
                    Log.i(TAG, "✅ SUCCESS: Game running successfully with core: ${data.coreFilePath}")
                    
                    // Nettoyer les infos de crash
                    crashPrefs.edit().clear().apply()
                } else {
                    Log.w(TAG, "⚠️ Error dialog is showing, not declaring success")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in success detection: ${e.message}")
            }
        }, CRASH_TIMEOUT_MS)
        
        // Configurer le type de contrôleur pour PSX (DualShock pour analog sticks)
        if (console.equals("psx", ignoreCase = true)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val controllers = retroView.getControllers()
                    Log.i(TAG, "[PSX] Available controllers: ${controllers.getOrNull(0)?.map { "id=${it.id} desc='${it.description}'" }}")
                    
                    if (controllers.isNotEmpty() && controllers[0].isNotEmpty()) {
                        // Chercher le contrôleur DualShock (essayer "dualshock" en priorité)
                        val dualshock = controllers[0].firstOrNull { 
                            it.description?.contains("dualshock", ignoreCase = true) == true
                        } ?: controllers[0].firstOrNull {
                            it.description?.contains("analog", ignoreCase = true) == true
                        }
                        
                        if (dualshock != null) {
                            retroView.setControllerType(0, dualshock.id)
                            Log.i(TAG, "[PSX] Controller type set to DualShock (id=${dualshock.id}, desc='${dualshock.description}')")
                        } else {
                            Log.w(TAG, "[PSX] DualShock controller not found. Using default.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[PSX] Error configuring controller type", e)
                }
            }, 1000)  // Attendre 1 seconde pour que le core soit complètement initialisé
        }
        
        // Configuration pour les jeux Zapper (Duck Hunt, etc.)
        // Port 1 = Gamepad (Start/Select), Port 2 = Zapper (Touch to shoot)
        // DOIT ATTENDRE que le core soit complètement chargé (1 seconde)
        if (isZapperGame) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    // Configurer le port comme RETRO_DEVICE_ZAPPER (258)
                    // CRITICAL: FCEUmm lit RETRO_DEVICE_POINTER seulement si nes_input.type[port] == RETRO_DEVICE_ZAPPER!
                    // get_mouse_input() n'est appelé que pour ZAPPER/ARKANOID (ligne 2686-2693 libretro.c)
                    // Port 2 (index 1) = Position traditionnelle du Zapper NES
                    retroView.setControllerType(1, 258)  // Port 2 (index 1) = RETRO_DEVICE_ZAPPER
                    Log.i(TAG, "[NES] Zapper configured as RETRO_DEVICE_ZAPPER (258) on port 2 (index 1)")
                    
                    runOnUiThread {
                        Toast.makeText(
                            this@RetroArchEmulatorActivity,
                            "Zapper detected! Port 2 (index 1)\nTouch game area to shoot",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[NES] Failed to configure Zapper: ${e.message}")
                }
            }, 1000)  // Attendre 1 seconde pour que le core soit complètement initialisé
        }

        // Configurer les extensions contrôleur pour N64
        if (console.equals("n64", ignoreCase = true)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.i(TAG, "[N64] Configuring controller extensions...")

                    // Charger les paramètres depuis SharedPreferences
                    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@RetroArchEmulatorActivity)
                    val prefix = "n64_"

                    // Mapping des positions spinner vers les valeurs Libretro :
                    // 0 = Controller Pak (1), 1 = Rumble Pak (2), 2 = Transfer Pak (5)
                    val pakValues = intArrayOf(1, 2, 5)

                    // TODO: Configurer les extensions contrôleur N64
                    // Les valeurs sont sauvegardées dans les préférences mais l'application dans LibretroDroid
                    // nécessite une investigation supplémentaire pour la méthode correcte
                    for (port in 0..3) {  // 4 ports maximum pour N64
                        try {
                            val pakPosition = prefs.getInt(prefix + "pak_port" + (port + 1), 0) // Default: Controller Pak
                            val pakValue = pakValues.getOrElse(pakPosition) { 1 } // Fallback to Controller Pak

                            val pakName = when (pakPosition) {
                                0 -> "Controller Pak"
                                1 -> "Rumble Pak"
                                2 -> "Transfer Pak"
                                else -> "Unknown"
                            }
                            Log.i(TAG, "[N64] Extension configured for port ${port + 1}: $pakName (value=$pakValue)")
                        } catch (e: Exception) {
                            Log.w(TAG, "[N64] Could not configure extension for port ${port + 1}: ${e.message}")
                        }
                    }


                } catch (e: Exception) {
                    Log.e(TAG, "[N64] Error configuring controller extensions", e)
                }
            }, 1000)  // Attendre 1 seconde pour que le core soit complètement initialisé
        }

        // Initialiser le CheatApplier
        cheatApplier = com.retroplay.cheat.CheatApplier(retroView)
        
        // === CHARGER LES DIP SWITCHES ET CORE OPTIONS ===
        // Attendre que le core expose ses variables (certains cores comme MAME prennent du temps)
        // Délai réduit à 2 secondes pour s'assurer que le core est complètement initialisé
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                try {
                    loadCoreVariables()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load core variables: ${e.message}")
                }
            }
        }, 2000)
        
        // Charger la save depuis le slot demandé (si loadSlot > 0)
        // IMPORTANT : Différer le chargement pour laisser le core s'initialiser
        if (loadSlot > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val saveFile = File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$loadSlot/$gameName.state")
                if (saveFile.exists()) {
                    try {
                        retroView.unserializeState(saveFile.readBytes())
                        Log.i(TAG, "[$console] Save state loaded from slot $loadSlot: ${saveFile.absolutePath}")
                        Toast.makeText(this, "[$console] Loaded from Slot $loadSlot", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "[$console] Error loading save from slot $loadSlot", e)
                        Toast.makeText(this, "[$console] Error loading save", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "[$console] No save found in slot $loadSlot")
                    Toast.makeText(this, "[$console] No save in Slot $loadSlot", Toast.LENGTH_SHORT).show()
                }
            }, 2000)  // Attendre 2 secondes pour que le core s'initialise complètement
        }
        
        // Charger et appliquer les codes de triche activés APRÈS le chargement complet du jeu
        // Pour les jeux PSX (PBP), le chargement initial est lent, donc on attend plus longtemps
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            loadAndApplyCheats()
        }, if (loadSlot > 0) 3000 else 8000)  // 8s pour NEW GAME, 3s pour LOAD SAVE
        
        // State pour capturer les bounds exacts du GLRetroView (pour Zapper)
        // Défini ICI (dans l'Activity) pour être accessible dans onZapperTouch
        val gameViewBounds = mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        
        setContent {
            ComposeEmulatorScreen(
                retroView = retroView,
                console = console,
                gameName = gameName,
                romPath = romPath,
                prefs = prefs,
                showMainMenu = showMainMenu,
                showGamePadSettings = showGamePadSettings,
                showAdvancedOverlaySettings = showAdvancedOverlaySettings,
                showQuickMenu = showQuickMenu,
                showGameInfoDialog = showGameInfoDialog,
                showCheatsDialog = showCheatsDialog,
                showSmartConfigDialog = showSmartConfigDialog,
                showPerGameConfigDialog = showPerGameConfigDialog,
                gameCRC = gameCRC,
                loadedCheats = loadedCheats,
                overlaysVisible = overlaysVisible,
                initialVariant = savedVariant,
                cheatApplier = cheatApplier,
                onVariantChanged = { newVariant ->
                    GamePadLayoutManager.saveVariant(prefs, console, newVariant)
                },
                onFinishActivity = {
                    // Fermer le jeu avec délai pour une transition fluide
                    safeFinishActivity(currentCoreFilePath, delayMs = 1200)
                },
                onSaveState = { slot ->
                    saveGameState(slot)
                },
                isZapperGame = isZapperGame,
                gameViewBounds = gameViewBounds,  // Passer le state pour capture
                onZapperTouch = { event ->
                    val lightgunSettings = com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console)
                    handleZapperTouch(
                        event, 
                        gameViewBounds.value, 
                        lightgunSettings.lightgunTriggerOnTouch, 
                        lightgunSettings.lightgunAllowOffscreen,
                        lightgunSettings.lightgunTriggerDelay,
                        lightgunSettings.lightgunPort
                    )
                },
                onLoadState = { slot ->
                    loadGameState(slot)
                },
                onHotkey = { action ->
                    handleHotkey(action)
                },
                // Quick Wins callbacks
                onToggleFastForward = {
                    toggleFastForward()
                },
                onToggleAudioMute = {
                    toggleAudioMute()
                },
                onCycleShader = {
                    cycleShader()
                },
                onToggleQuickActionsBar = {
                    toggleQuickActionsBar()
                },
                onConfigureZapper = {
                    configureZapperManually()  // Configure Zapper et redémarre ROM
                },
                onToggleCrosshairMode = {
                    toggleCrosshairMode()  // Cycle crosshair mode (RetroPlay/FCEUmm/Both/None)
                },
                isFastForwardActive = isFastForwardActive.value,
                audioMuted = audioMuted.value,
                currentShaderName = currentShader.value.displayName,
                quickActionsBarVisible = quickActionsBarVisible.value,
                crosshairMode = crosshairMode.value,
                showDipSwitchDialog = showDipSwitchDialog,
                showCoreOptionsDialog = showCoreOptionsDialog,
                dipSwitches = dipSwitches,
                coreOptions = coreOptions,
                showDiskSwapperDialog = showDiskSwapperDialog,
                availableDisks = availableDisks,
                currentDisk = currentDisk,
                onTakeScreenshot = {
                    takeScreenshot()
                },
                onLoadCustomCfg = {
                    // Lancer le file picker pour sélectionner un .cfg
                    pickCustomCfgLauncher.launch(arrayOf("*/*"))
                }
            )
            
            // Dialog d'erreur de chargement du core
            if (showCoreErrorDialog.value) {
                CoreErrorDialog(
                    coreName = failedCoreName,
                    gameName = gameName,
                    onChangeCore = {
                        showCoreErrorDialog.value = false
                        // Attendre un peu que le dialog se ferme avant d'ouvrir le suivant
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            showCoreSelectorFromError.value = true
                        }, 100)
                    },
                    onRetry = {
                        showCoreErrorDialog.value = false
                        // Nettoyer les prefs de crash avant de réessayer
                        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        crashPrefs.edit().clear().apply()
                        // Fermer le jeu - l'utilisateur relancera depuis GameDetailsActivity
                        safeFinishActivity(currentCoreFilePath, delayMs = 0)
                    },
                    onCancel = {
                        showCoreErrorDialog.value = false
                        // Retourner à GameDetailsActivity
                        safeFinishActivity(currentCoreFilePath, delayMs = 0)
                    }
                )
            }
            
            // Dialog de sélection de core (après erreur)
            if (showCoreSelectorFromError.value) {
                CoreSelectorDialog(
                    console = console,
                    currentGamePath = romPath,
                    onCoreSelected = { coreInfo ->
                        showCoreSelectorFromError.value = false
                        
                        // Nettoyer les prefs de crash
                        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        crashPrefs.edit().clear().apply()
                        
                        // Sauvegarder le nouveau core comme override
                        val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
                            romPath.substringAfter("/GameLibrary-Data/")
                        } else {
                            ""
                        }
                        if (relativePath.isNotEmpty()) {
                            val overrideManager = CoreOverrideManager.getInstance()
                            overrideManager.setOverride(relativePath, coreInfo.coreId, "User selected after core error")
                            Log.i(TAG, "Core override saved: $relativePath → ${coreInfo.coreId}")
                        }
                        // Afficher un message de confirmation avant de fermer
                        coreChangeConfirmMessage = "Core changed to ${coreInfo.displayName}.\n\nRelaunch the game from the menu to apply."
                        showCoreChangeConfirmDialog.value = true
                    },
                    onResetToDefault = {
                        showCoreSelectorFromError.value = false
                        
                        // Nettoyer les prefs de crash
                        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        crashPrefs.edit().clear().apply()
                        
                        // Supprimer l'override
                        val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
                            romPath.substringAfter("/GameLibrary-Data/")
                        } else {
                            ""
                        }
                        if (relativePath.isNotEmpty()) {
                            val overrideManager = CoreOverrideManager.getInstance()
                            overrideManager.removeOverride(relativePath)
                            Log.i(TAG, "Core override removed: $relativePath")
                        }
                        // Afficher un message de confirmation avant de fermer
                        coreChangeConfirmMessage = "Core reset to default.\n\nRelaunch the game from the menu to apply."
                        showCoreChangeConfirmDialog.value = true
                    },
                    onDismiss = {
                        showCoreSelectorFromError.value = false
                    }
                )
            }
            
            // Dialog de confirmation du changement de core
            if (showCoreChangeConfirmDialog.value) {
                AlertDialog(
                    onDismissRequest = { showCoreChangeConfirmDialog.value = false },
                    title = {
                        Text(
                            text = "Core Changed",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = coreChangeConfirmMessage,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCoreChangeConfirmDialog.value = false
                                // Fermer le jeu avec délai pour une transition fluide
                                safeFinishActivity(currentCoreFilePath, delayMs = 1200)
                            }
                        ) {
                            Text("OK", color = Color(0xFF4CAF50), fontSize = 16.sp)
                        }
                    },
                    containerColor = Color(0xFF2C2C2C),
                    tonalElevation = 8.dp
                )
            }
            
            // === DIP SWITCHES DIALOG ===
            if (showDipSwitchDialog.value) {
                DipSwitchDialog(
                    gameName = gameName,
                    dipSwitches = dipSwitches,
                    onApply = { modifiedValues ->
                        // Extraire le coreId
                        val coreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
                        val gameId = File(romPath).nameWithoutExtension
                        
                        // Sauvegarder les modifications
                        CoreVariableManager.saveVariables(this@RetroArchEmulatorActivity, gameId, coreId, modifiedValues)
                        
                        // Appliquer au core
                        val updatedVars = dipSwitches.map { dip ->
                            if (modifiedValues.containsKey(dip.key)) {
                                dip.copy(currentValue = modifiedValues[dip.key]!!)
                            } else {
                                dip
                            }
                        }
                        val libretroVars = CoreVariableManager.toLibretroVariables(updatedVars)
                        retroView.updateVariables(*libretroVars)
                        
                        // Mettre à jour la liste locale
                        dipSwitches.clear()
                        dipSwitches.addAll(updatedVars)
                        
                        Log.i(TAG, "Applied ${modifiedValues.size} DIP switch changes")
                    },
                    onDismiss = { showDipSwitchDialog.value = false }
                )
            }
            
            // === CORE OPTIONS DIALOG ===
            if (showCoreOptionsDialog.value) {
                CoreOptionsDialog(
                    gameName = gameName,
                    coreOptions = coreOptions,
                    onApply = { modifiedValues ->
                        // Extraire le coreId et nom du core
                        val coreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
                        val coreName = coreId.replace("_libretro_android", "").replaceFirstChar { it.uppercase() }
                        
                        // NOUVEAU: Sauvegarder dans le fichier .cfg au lieu de SharedPreferences
                        CoreConfigManager.saveConfig(this@RetroArchEmulatorActivity, coreName, modifiedValues)
                        Log.i(TAG, "[$coreName] Saved ${modifiedValues.size} options to .cfg file")
                        
                        // Appliquer au core
                        val updatedVars = coreOptions.map { opt ->
                            if (modifiedValues.containsKey(opt.key)) {
                                opt.copy(currentValue = modifiedValues[opt.key]!!)
                            } else {
                                opt
                            }
                        }
                        val libretroVars = CoreVariableManager.toLibretroVariables(updatedVars)
                        retroView.updateVariables(*libretroVars)
                        
                        // Mettre à jour la liste locale
                        coreOptions.clear()
                        coreOptions.addAll(updatedVars)
                        
                        Log.i(TAG, "Applied ${modifiedValues.size} core option changes to running core")
                    },
                    onDismiss = { showCoreOptionsDialog.value = false }
                )
            }
            
            // === GAME INFO DIALOG (Database) ===
            if (showGameInfoDialog.value) {
                val gameCRC = intent.getStringExtra("gameCRC")
                val gameInfo = gameCRC?.let { com.retroplay.database.DatabaseManager.lookupGame(it, console) }
                val cheatFile = gameInfo?.let { com.retroplay.database.DatabaseManager.getCheatsPath(it, console) }
                
                GameInfoDialog(
                    gameInfo = gameInfo,
                    gameCRC = gameCRC,
                    cheatFile = cheatFile,
                    onDismiss = { showGameInfoDialog.value = false },
                    onOpenPerGameConfig = { crc, name ->
                        perGameConfigCRC = crc
                        perGameConfigGameName = name
                        showGameInfoDialog.value = false
                        showPerGameConfigDialog.value = true
                    }
                )
            }
            
            // === PER-GAME CONFIG DIALOG ===
            if (showPerGameConfigDialog.value && perGameConfigCRC != null) {
                PerGameConfigDialog(
                    gameCRC = perGameConfigCRC!!,
                    gameName = perGameConfigGameName,
                    onDismiss = { showPerGameConfigDialog.value = false },
                    onSave = {
                        // Config was saved, reload it if needed
                        Log.i(TAG, "Per-game config saved for CRC: $perGameConfigCRC")
                        // TODO: Apply config to running emulator (requires Run-Ahead/Rewind APIs)
                    }
                )
            }
            
            // === CHEATS DIALOG (use existing CheatSelectionDialog) ===
            if (showCheatsDialog.value && loadedCheats.isNotEmpty()) {
                val cheatManager = remember { com.retroplay.cheat.CheatManager(this) }
                var cheats by remember(showCheatsDialog.value) { 
                    mutableStateOf(loadedCheats)
                }
                
                var showAddCheatDialog by remember { mutableStateOf(false) }
                
                com.retroplay.cheat.CheatSelectionDialog(
                    gameName = gameName,
                    console = console,
                    cheats = cheats,
                    onDismiss = { showCheatsDialog.value = false },
                    onCheatsChanged = { updatedCheats ->
                        cheats = updatedCheats.toMutableList()
                        // Sauvegarder les modifications
                        cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
                        // Appliquer immédiatement au core
                        cheatApplier.applyCheatsList(updatedCheats)
                        // Update Activity state
                        loadedCheats.clear()
                        loadedCheats.addAll(updatedCheats)
                    },
                    onAddCustomCheat = {
                        showAddCheatDialog = true
                    }
                )
                
                // Dialog pour ajouter un cheat custom (si disponible)
                if (showAddCheatDialog) {
                    // Simple input dialog for custom cheats
                    AlertDialog(
                        onDismissRequest = { showAddCheatDialog = false },
                        title = { Text("Add Custom Cheat") },
                        text = {
                            Column {
                                Text("Feature coming soon - Use Main Menu → Cheat Codes for full editor")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAddCheatDialog = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
            
            // === SMART CONFIG DIALOG ===
            if (showSmartConfigDialog.value) {
                SmartConfigDialog(
                    onDismiss = { showSmartConfigDialog.value = false },
                    onSettingsChanged = {
                        Log.i(TAG, "Smart Config settings saved to retroplay.cfg")
                        Toast.makeText(this, "Smart Config saved to retroplay.cfg", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            
            // === DISK SWAPPER DIALOG ===
            if (showDiskSwapperDialog.value && availableDisks > 1) {
                DiskSwapperDialog(
                    availableDisks = availableDisks,
                    currentDiskIndex = currentDisk,
                    onDiskSelected = { diskIndex ->
                        lifecycleScope.launch {
                            try {
                                retroView.changeDisk(diskIndex)
                                currentDisk = diskIndex
                                Toast.makeText(
                                    this@RetroArchEmulatorActivity, 
                                    "Swapped to Disk ${diskIndex + 1}", 
                                    Toast.LENGTH_SHORT
                                ).show()
                                Log.i(TAG, "Disk swapped to index: $diskIndex")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to swap disk", e)
                                Toast.makeText(
                                    this@RetroArchEmulatorActivity, 
                                    "Failed to swap disk", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDismiss = { showDiskSwapperDialog.value = false }
                )
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun setupFullscreenMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun getCorePath(console: String): String {
        // Vérifier s'il y a un override de core pour ce jeu spécifique
        val gameName = intent.getStringExtra("gameName") ?: ""
        val romPath = intent.getStringExtra("romPath") ?: ""
        
        // Construire le chemin relatif du jeu (ex: "fbneo/sega/afighter.zip")
        val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
            romPath.substringAfter("/GameLibrary-Data/")
        } else {
            ""
        }
        
        // Vérifier l'override
        var coreFileName: String? = null
        if (relativePath.isNotEmpty()) {
            val overrideManager = CoreOverrideManager.getInstance()
            val overrideCoreId = overrideManager.getCoreOverride(relativePath)
            
            if (overrideCoreId != null) {
                Log.i(TAG, "Using core override for $gameName: $overrideCoreId")
                // Mapper le coreId vers le fichier .so
                coreFileName = when (overrideCoreId.lowercase()) {
                    "mame2010" -> "mame2010_libretro_android.so"
                    "mame2003_plus" -> "mame2003_plus_libretro_android.so"
                    "mame2003" -> "mame2003_libretro_android.so"
                    "fbneo" -> "fbneo_libretro_android.so"
                    "fceumm" -> "fceumm_libretro_android.so"
                    "snes9x" -> "snes9x_libretro_android.so"
                    "parallel_n64" -> "parallel_n64_libretro_android.so"
                    "mupen64plus_next" -> "mupen64plus_next_libretro_android.so"
                    "mupen64plus_next_gles3" -> "mupen64plus_next_libretro_android.so"
                    "mupen64plus_next_gles2" -> "mupen64plus_next_gles2_libretro_android.so"
                    "gambatte" -> "gambatte_libretro_android.so"
                    "mgba" -> "libmgba_libretro_android.so"
                    "pcsx_rearmed" -> "pcsx_rearmed_libretro_android.so"
                    "ppsspp" -> "ppsspp_libretro_android.so"
                    "genesis_plus_gx" -> "genesis_plus_gx_libretro_android.so"
                    "picodrive" -> "picodrive_libretro_android.so"
                    else -> null
                }
            }
        }
        
        // Si pas d'override, utiliser la logique par défaut basée sur la console
        if (coreFileName == null) {
            // Pour les sous-consoles (ex: fbneo/sega), utiliser le parent (fbneo)
            val consoleKey = if (console.contains("/")) {
                console.substringBefore("/").lowercase()
            } else {
                console.lowercase()
            }
            
            coreFileName = when (consoleKey) {
                // Nintendo
                "nes" -> "fceumm_libretro_android.so"
                "snes" -> "snes9x_libretro_android.so"
                "n64" -> "parallel_n64_libretro_android.so"
                "gb", "gbc" -> "gambatte_libretro_android.so"
                "gba" -> "libmgba_libretro_android.so"
                
                // Sony
                "psx", "ps1", "playstation" -> "pcsx_rearmed_libretro_android.so"
                "psp" -> "ppsspp_libretro_android.so"
                
                // Sega
                "genesis", "megadrive", "md" -> "genesis_plus_gx_libretro_android.so"
                "scd", "segacd" -> "genesis_plus_gx_libretro_android.so"
                "mastersystem", "sms", "segasms" -> "genesis_plus_gx_libretro_android.so"
                "gamegear", "gg", "segagg" -> "genesis_plus_gx_libretro_android.so"
                "32x", "sega32x" -> "picodrive_libretro_android.so"
                
                // Atari
                "atari2600", "atari", "a2600" -> "stella2014_libretro_android.so"
                "atari5200", "a5200" -> "a5200_libretro_android.so"
                "atari7800", "a7800" -> "prosystem_libretro_android.so"
                "lynx", "atarilynx" -> "mednafen_lynx_libretro_android.so"
                
                // Other
                "ngp", "ngc", "neogeopocket" -> "mednafen_ngp_libretro_android.so"
                "ws", "wsc", "wonderswan" -> "mednafen_wswan_libretro_android.so"
                "pce", "turbografx", "pcengine" -> "mednafen_pce_libretro_android.so"
                "arcade" -> "mame2003_plus_libretro_android.so"
                "mame" -> "mame2010_libretro_android.so"
                "fbneo", "neogeo", "cps1", "cps2" -> "fbneo_libretro_android.so"
                
                else -> {
                    Log.w(TAG, "No native core for console: $console, using fceumm fallback")
                    "fceumm_libretro_android.so"
                }
            }
        }
        
        // Vérifier si le core est téléchargé dans RetroPlay-Data/cores/
        val downloadedCorePath = CoreDownloader.getCorePathByFileName(this, coreFileName)
        if (downloadedCorePath != null) {
            Log.i(TAG, "Using downloaded core: $downloadedCorePath")
            return downloadedCorePath
        }
        
        // Sinon, utiliser le core embarqué (juste le nom du fichier)
        Log.i(TAG, "Using embedded core: $coreFileName")
        return coreFileName
    }
    
    /**
     * Retourne la liste des cores à essayer dans l'ordre (fallback automatique)
     * Si le premier core crash, on essaie le suivant automatiquement
     */
    private fun getCoreFallbacks(console: String): List<String> {
        val consoleKey = if (console.contains("/")) {
            console.substringBefore("/").lowercase()
        } else {
            console.lowercase()
        }
        
        return when (consoleKey) {
            // Arcade: FBNeo (plus compatible) → MAME2003+ → MAME2003 → MAME2010
            "arcade" -> listOf(
                "fbneo_libretro_android.so",
                "mame2003_plus_libretro_android.so",
                "mame2003_libretro_android.so",
                "mame2010_libretro_android.so"
            )
            "mame" -> listOf(
                "mame2003_plus_libretro_android.so",
                "mame2003_libretro_android.so",
                "mame2010_libretro_android.so",
                "fbneo_libretro_android.so"
            )
            "fbneo", "neogeo", "cps1", "cps2" -> listOf(
                "fbneo_libretro_android.so",
                "mame2003_plus_libretro_android.so"
            )
            
            // N64: ParaLLEl (performant) → Mupen64Plus (compatible)
            "n64" -> listOf(
                "parallel_n64_libretro_android.so",
                "mupen64plus_next_libretro_android.so"
            )
            
            // Pour les autres consoles, un seul core disponible
            else -> listOf(getCorePath(console))
        }
    }
    
    // Charger et appliquer les codes de triche au démarrage
    private fun loadAndApplyCheats() {
        try {
            val cheatManager = com.retroplay.cheat.CheatManager(this)
            loadedCheats.clear()
            loadedCheats.addAll(cheatManager.loadCheatsForGame(console, gameName, romPath))
            
            if (loadedCheats.isNotEmpty()) {
                val enabledCount = loadedCheats.count { it.enabled }
                if (enabledCount > 0) {
                    Log.i(TAG, "[$console] Loading $enabledCount active cheat(s) for $gameName")
                    cheatApplier.applyCheatsList(loadedCheats)
                    
                    runOnUiThread {
                        Toast.makeText(this, "[$console] $enabledCount cheat(s) active", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "[$console] No active cheats for $gameName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cheats", e)
        }
    }
    
    // Sauvegarder l'état des cheats (enabled/disabled) dans le fichier .cht
    private fun saveCheatStates() {
        try {
            val cheatManager = com.retroplay.cheat.CheatManager(this)
            cheatManager.saveEnabledCheats(console, gameName, loadedCheats)
            Log.i(TAG, "[$console] Saved cheat states for $gameName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cheat states", e)
        }
    }
    
    // Sauvegarder l'état du jeu dans un slot (organisé par console/slot)
    private fun saveGameState(slot: Int) {
        try {
            // Structure : saves/{console}/slot{slot}/{gameName}.state
            val slotDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$slot")
            if (!slotDir.exists()) {
                slotDir.mkdirs()
            }
            
            val saveFile = File(slotDir, "${gameName}.state")
            val stateData = retroView.serializeState()
            saveFile.writeBytes(stateData)
            
            Log.i(TAG, "[$console] Game state saved to slot $slot: ${saveFile.absolutePath}")
            runOnUiThread {
                Toast.makeText(this, "[$console] Saved to Slot $slot", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving game state to slot $slot", e)
            runOnUiThread {
                Toast.makeText(this, "Error saving game", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Charger l'état du jeu depuis un slot (organisé par console/slot)
    private fun loadGameState(slot: Int) {
        try {
            // Structure : saves/{console}/slot{slot}/{gameName}.state
            val saveFile = File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$slot/${gameName}.state")
            if (saveFile.exists()) {
                retroView.unserializeState(saveFile.readBytes())
                Log.i(TAG, "[$console] Game state loaded from slot $slot: ${saveFile.absolutePath}")
                runOnUiThread {
                    Toast.makeText(this, "[$console] Loaded from Slot $slot", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "No save state found for slot $slot in $console")
                runOnUiThread {
                    Toast.makeText(this, "[$console] No save in Slot $slot", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading game state from slot $slot", e)
            runOnUiThread {
                Toast.makeText(this, "Error loading game", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // État pour fast forward et pause (MutableState pour reactivity Compose)
    private val isFastForwardActive = mutableStateOf(false)
    private var fastForwardRatio = 2  // 2x par défaut (2x, 3x, 4x disponibles)
    private var isPaused = false
    private val currentSaveSlot = 0  // Slot par défaut (0-9)
    
    // État pour audio mute (MutableState pour reactivity Compose)
    private val audioMuted = mutableStateOf(false)
    
    // État pour shader selection (MutableState pour reactivity Compose)
    private val currentShader = mutableStateOf(com.retroplay.shader.ShaderManager.ShaderPreset.DEFAULT)
    
    // État pour QuickActionsBar visibility (MutableState pour reactivity Compose)
    private val quickActionsBarVisible = mutableStateOf(true)
    
    // État pour mode d'affichage du crosshair Zapper (MutableState pour reactivity Compose)
    private val crosshairMode = mutableStateOf(CrosshairMode.RETROPLAY_ONLY)
    
    // Gérer les hotkeys RetroArch
    private fun handleHotkey(action: String) {
        Log.i(TAG, "Hotkey triggered: $action")
        when (action) {
            // Save/Load states
            "save_state" -> {
                saveGameState(currentSaveSlot)
            }
            "load_state" -> {
                loadGameState(currentSaveSlot)
            }
            "state_slot_increase" -> {
                // TODO: Implémenter changement de slot (nécessite UI feedback)
                Log.i(TAG, "State slot increase (not implemented yet)")
                runOnUiThread {
                    Toast.makeText(this, "Slot+ (not implemented)", Toast.LENGTH_SHORT).show()
                }
            }
            "state_slot_decrease" -> {
                // TODO: Implémenter changement de slot (nécessite UI feedback)
                Log.i(TAG, "State slot decrease (not implemented yet)")
                runOnUiThread {
                    Toast.makeText(this, "Slot- (not implemented)", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Fast forward
            "toggle_fast_forward" -> {
                toggleFastForward()
            }
            "hold_fast_forward" -> {
                // Hold fast forward (maintenir pour accélérer)
                retroView.frameSpeed = fastForwardRatio
                isFastForwardActive.value = true
                Log.i(TAG, "[FAST_FORWARD] Hold: ${fastForwardRatio}x")
            }
            
            // Audio mute
            "audio_mute_toggle" -> {
                toggleAudioMute()
            }
            
            // Shader cycle
            "shader_next" -> {
                cycleShader()
            }
            
            // Rewind (nécessite support du core)
            "rewind" -> {
                Log.i(TAG, "Rewind (not supported by LibretroDroid)")
                runOnUiThread {
                    Toast.makeText(this, "Rewind not supported", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Reset
            "reset" -> {
                retroView.reset()
                Log.i(TAG, "Game reset")
                runOnUiThread {
                    Toast.makeText(this, "Game Reset", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Pause toggle
            "pause_toggle" -> {
                isPaused = !isPaused
                if (isPaused) {
                    retroView.onPause()
                    Log.i(TAG, "Game paused")
                    runOnUiThread {
                        Toast.makeText(this, "Game Paused", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    retroView.onResume()
                    Log.i(TAG, "Game resumed")
                    runOnUiThread {
                        Toast.makeText(this, "Game Resumed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // Screenshot
            "screenshot" -> {
                Log.i(TAG, "Screenshot (not implemented yet)")
                runOnUiThread {
                    Toast.makeText(this, "Screenshot not implemented", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Shaders
            "shader_next", "shader_prev" -> {
                Log.i(TAG, "Shader cycling (not implemented yet)")
                runOnUiThread {
                    Toast.makeText(this, "Shader cycling not implemented", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Slow motion
            "toggle_slowmotion" -> {
                // LibretroDroid frameSpeed est un Int (pas de valeurs < 1)
                Log.i(TAG, "Slow motion (not supported - frameSpeed must be >= 1)")
                runOnUiThread {
                    Toast.makeText(this, "Slow motion not supported", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Frame advance
            "frame_advance" -> {
                // Frame advance = pause + resume (1 frame) + pause
                // LibretroDroid va rendre 1 frame puis se re-pauser
                if (!isPaused) {
                    retroView.onPause()
                    isPaused = true
                }
                // Resume pour 1 frame, puis re-pause via handler
                retroView.onResume()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    retroView.onPause()
                }, 16)  // ~1 frame à 60fps
                Log.i(TAG, "Frame advance (1 frame)")
                runOnUiThread {
                    Toast.makeText(this, "Frame +1", Toast.LENGTH_SHORT).show()
                }
            }
            
            else -> {
                Log.w(TAG, "Unknown hotkey: $action")
            }
        }
    }
    
    // Quick Win #1: Fast Forward Toggle
    private fun toggleFastForward() {
        isFastForwardActive.value = !isFastForwardActive.value
        val speed = if (isFastForwardActive.value) fastForwardRatio else 1
        retroView.frameSpeed = speed
        Log.i(TAG, "[FAST_FORWARD] ${if (isFastForwardActive.value) "ENABLED (${fastForwardRatio}x)" else "DISABLED (1x)"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_fast_forward_active", isFastForwardActive.value).apply()
        
        runOnUiThread {
            Toast.makeText(
                this,
                if (isFastForwardActive.value) "Fast Forward: ${fastForwardRatio}x" else "Normal Speed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Quick Win #2: Audio Mute Toggle
    private fun toggleAudioMute() {
        audioMuted.value = !audioMuted.value
        retroView.audioEnabled = !audioMuted.value
        Log.i(TAG, "[AUDIO] ${if (audioMuted.value) "MUTED" else "UNMUTED"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_audio_muted", audioMuted.value).apply()
        
        runOnUiThread {
            Toast.makeText(
                this,
                if (audioMuted.value) "Audio Muted" else "Audio Unmuted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // QuickActionsBar Visibility Toggle
    private fun toggleQuickActionsBar() {
        quickActionsBarVisible.value = !quickActionsBarVisible.value
        Log.i(TAG, "[QUICK_ACTIONS_BAR] ${if (quickActionsBarVisible.value) "VISIBLE" else "HIDDEN"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_quick_actions_bar_visible", quickActionsBarVisible.value).apply()
        
        runOnUiThread {
            Toast.makeText(
                this,
                if (quickActionsBarVisible.value) "Quick Actions Bar Visible" else "Quick Actions Bar Hidden",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Quick Win #4: Shader Cycle (Next shader)
    private fun cycleShader() {
        currentShader.value = com.retroplay.shader.ShaderManager.getNextShader(currentShader.value)
        val shaderConfig = com.retroplay.shader.ShaderManager.getShaderConfig(currentShader.value)
        retroView.shader = shaderConfig
        
        Log.i(TAG, "[SHADER] Switched to: ${currentShader.value.displayName}")
        Log.i(TAG, "[SHADER] ShaderConfig type: ${shaderConfig.javaClass.simpleName}")
        
        // Sauvegarder dans SharedPreferences
        prefs.edit().putString("emulation_shader_preset", currentShader.value.name).apply()
        
        runOnUiThread {
            Toast.makeText(
                this,
                "Shader: ${currentShader.value.displayName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Toggle Crosshair Mode (Cycle entre RetroPlay / FCEUmm / Both / None)
    private fun toggleCrosshairMode() {
        crosshairMode.value = crosshairMode.value.next()
        Log.i(TAG, "[CROSSHAIR] Mode: ${crosshairMode.value.displayName}")
        
        // Sauvegarder dans SharedPreferences
        prefs.edit().putString("emulation_crosshair_mode", crosshairMode.value.name).apply()
        
        // Si on est en jeu NES, mettre à jour la config du core dynamiquement
        if (console == "nes") {
            val config = CoreConfigManager.loadConfig(this, "FCEUmm").toMutableMap()
            config["fceumm_show_crosshair"] = if (crosshairMode.value.showFCEUmmCrosshair()) "enabled" else "disabled"
            CoreConfigManager.saveConfig(this, "FCEUmm", config)
            
            // Appliquer au core sans redémarrer
            val nesVariables = config.map { (key, value) -> com.swordfish.libretrodroid.Variable(key, value) }.toTypedArray()
            retroView.updateVariables(*nesVariables)
            Log.i(TAG, "[CROSSHAIR] Updated fceumm_show_crosshair = ${config["fceumm_show_crosshair"]}")
        }
        
        runOnUiThread {
            Toast.makeText(
                this,
                "Crosshair: ${crosshairMode.value.displayName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Lifecycle managed by lifecycle.addObserver(retroView)
    
    override fun onDestroy() {
        try {
            Log.i(TAG, "🔴 onDestroy called - cleaning up core")
            // Ne pas appeler retroView.onDestroy() manuellement car lifecycle.addObserver le fait déjà
            // Juste logger pour le debug
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }
}

// Handle PadKit events (List of events from gamepads)
private fun handlePadKitEvent(
    events: List<InputEvent>,
    retroView: GLRetroView,
    showMainMenu: MutableState<Boolean>,
    settings: TouchControllerSettingsManager.Settings? = null
) {
    // Intercepter le bouton menu (comme Lemuroid le fait)
    val menuEvent = events.firstOrNull { 
        it is InputEvent.Button && it.id == KeyEvent.KEYCODE_BUTTON_MODE
    }
    
    if (menuEvent != null && (menuEvent as InputEvent.Button).pressed) {
        Log.i("NativeComposeEmulator", "Menu button pressed, opening main menu")
        showMainMenu.value = true
        return  // Ne pas envoyer l'événement menu à l'émulateur
    }
    
    // Traiter tous les autres événements
    events.forEach { event ->
        when (event) {
            is InputEvent.Button -> {
                val keyCode = event.id
                if (keyCode != KeyEvent.KEYCODE_BUTTON_MODE) {
                    val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                    retroView.sendKeyEvent(action, keyCode)
                }
            }
            
            is InputEvent.DiscreteDirection -> {
                // D-Pad et directions discrètes
                val source = when (event.id) {
                    0 -> GLRetroView.MOTION_SOURCE_DPAD
                    1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                    2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                    else -> GLRetroView.MOTION_SOURCE_DPAD
                }
                retroView.sendMotionEvent(source, event.direction.x, -event.direction.y)
            }
            
            is InputEvent.ContinuousDirection -> {
                // Analog sticks (mouvements continus)
                // Note: Dans ComposeTouchLayouts: MOTION_SOURCE_LEFT_STICK = 1, MOTION_SOURCE_RIGHT_STICK = 2
                var stickId = event.id
                
                // Appliquer swap si demandé (1 et 2 seulement, pas le DPAD qui est 0)
                if (settings != null && settings.swapAnalogSticks && (stickId == 1 || stickId == 2)) {
                    stickId = if (stickId == 1) 2 else 1
                }
                
                val source = when (stickId) {
                    1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT   // ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK
                    2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT  // ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK
                    else -> GLRetroView.MOTION_SOURCE_DPAD       // ID 0 = DPAD
                }
                
                // Appliquer inversion Y si demandé
                val yAxis = if (settings?.invertAnalogY == true) event.direction.y else -event.direction.y
                retroView.sendMotionEvent(source, event.direction.x, yAxis)
            }
        }
    }
}

@Composable
private fun ComposeEmulatorScreen(
    retroView: GLRetroView,
    console: String,
    gameName: String,
    romPath: String,
    prefs: SharedPreferences,
    showMainMenu: MutableState<Boolean>,
    showGamePadSettings: MutableState<Boolean>,
    showAdvancedOverlaySettings: MutableState<Boolean>,
    showQuickMenu: MutableState<Boolean>,
    showGameInfoDialog: MutableState<Boolean>,
    showCheatsDialog: MutableState<Boolean>,
    showSmartConfigDialog: MutableState<Boolean>,
    showPerGameConfigDialog: MutableState<Boolean>,
    gameCRC: String?,
    loadedCheats: List<com.retroplay.cheat.CheatManager.Cheat>,
    overlaysVisible: MutableState<Boolean>,
    initialVariant: GamePadLayoutManager.LayoutVariant,
    cheatApplier: com.retroplay.cheat.CheatApplier,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onFinishActivity: () -> Unit,
    onHotkey: (String) -> Unit,  // Callback pour hotkeys
    showDipSwitchDialog: MutableState<Boolean>,
    showCoreOptionsDialog: MutableState<Boolean>,
    dipSwitches: androidx.compose.runtime.snapshots.SnapshotStateList<CoreVariable>,
    coreOptions: androidx.compose.runtime.snapshots.SnapshotStateList<CoreVariable>,
    isZapperGame: Boolean = false,
    gameViewBounds: MutableState<androidx.compose.ui.geometry.Rect?>,  // Bounds du GLRetroView
    onZapperTouch: (android.view.MotionEvent) -> Boolean = { false },
    onLoadCustomCfg: (() -> Unit)? = null,  // Callback pour file picker
    // Quick Wins
    onToggleFastForward: () -> Unit = {},
    onToggleAudioMute: () -> Unit = {},
    onCycleShader: () -> Unit = {},
    onToggleQuickActionsBar: () -> Unit = {},
    onConfigureZapper: () -> Unit = {},  // TEST: Configure Zapper manuellement
    onToggleCrosshairMode: () -> Unit = {},  // Toggle Crosshair mode (RetroPlay/FCEUmm/Both/None)
    isFastForwardActive: Boolean = false,
    audioMuted: Boolean = false,
    currentShaderName: String = "None",
    quickActionsBarVisible: Boolean = true,
    crosshairMode: CrosshairMode = CrosshairMode.RETROPLAY_ONLY,
    // Disk Swapper & Screenshot
    showDiskSwapperDialog: MutableState<Boolean>,
    availableDisks: Int = 0,
    currentDisk: Int = 0,
    onTakeScreenshot: () -> Unit = {}
) {
    // NO Radial/Lemuroid settings needed - RetroArch overlays only!
    
    // État debug mode (accessible partout dans le Composable)
    val showDebug = remember { prefs.getBoolean("overlay_debug_mode", false) }
    val debugModeState = remember { mutableStateOf(showDebug) }
    
    // Variante de layout (state mutable)
    var layoutVariant by remember {
        mutableStateOf(initialVariant)
    }
    
    // État pour le switch de layout RetroArch (overrides la préférence)
    var currentRetroArchLayout by remember { mutableStateOf<String?>(null) }
    
    // Cooldown pour éviter la réouverture immédiate du QuickMenu après fermeture
    var lastMenuCloseTime by remember { mutableStateOf(0L) }
    val cooldownMs = 800L  // 800ms de cooldown après fermeture
    
    // Fonction helper pour fermer le QuickMenu avec enregistrement du cooldown
    fun closeQuickMenuWithCooldown() {
        lastMenuCloseTime = System.currentTimeMillis()
        showQuickMenu.value = false
        retroView.onResume()
        Log.d("ComposeEmulator", "QuickMenu closed with cooldown")
    }
    
    // Détection de l'orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    // Gestion du bouton back avec un seul handler pour éviter les conflits
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        
        when {
            // 1. QuickMenu ouvert -> Fermer avec cooldown
            showQuickMenu.value -> {
                closeQuickMenuWithCooldown()
            }
            
            // 2. MainMenu ou GamePadSettings ouvert -> Fermer
            showMainMenu.value || showGamePadSettings.value -> {
                showMainMenu.value = false
                showGamePadSettings.value = false
                // Si QuickMenu n'est pas ouvert, reprendre le jeu
                if (!showQuickMenu.value) {
                    retroView.onResume()
                }
                Log.d("ComposeEmulator", "Back: MainMenu/Settings closed")
            }
            
            // 3. Aucun menu ouvert -> Ouvrir QuickMenu (avec cooldown)
            else -> {
                val timeSinceClose = currentTime - lastMenuCloseTime
                if (timeSinceClose > cooldownMs) {
                    showQuickMenu.value = true
                    retroView.onPause()
                    Log.d("ComposeEmulator", "Back: QuickMenu opened")
                } else {
                    Log.d("ComposeEmulator", "Back: Cooldown active (${cooldownMs - timeSinceClose}ms remaining)")
                }
            }
        }
    }
    
    // Récupérer le layout approprié
    val layout = if (layoutVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
        // Utiliser getLayoutWithContext pour RetroArch
        GamePadLayoutManager.getLayoutWithContext(
            console = console,
            variant = layoutVariant,
            context = retroView.context,
            prefs = prefs,
            onButtonPress = { keyCodes ->
                keyCodes.forEach { keyCode ->
                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
                }
            },
            onButtonRelease = { keyCodes ->
                keyCodes.forEach { keyCode ->
                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, keyCode, 0)
                }
            },
            onLayoutSwitch = { requestedLayout ->
                // Mapper le layout demandé à l'orientation physique du device
                val mappedLayout = mapLayoutToDeviceOrientation(
                    requestedLayout = requestedLayout,
                    isLandscape = isLandscape
                )
                currentRetroArchLayout = mappedLayout
                Log.i("ComposeEmulator", "RetroArch layout switch: requested='$requestedLayout' mapped='$mappedLayout' (device=${if (isLandscape) "landscape" else "portrait"})")
            },
            onMenuToggle = {
                // Ouvrir le menu principal
                Log.i("ComposeEmulator", "Menu toggle from RetroArch overlay")
                showMainMenu.value = true
            }
        )
    } else {
        // Utiliser getLayout normal pour Lemuroid
        GamePadLayoutManager.getLayout(console, layoutVariant)
    }
    
    // Contraintes pour le layout (style Lemuroid)
    val constraintSet = if (isLandscape) {
        // Paysage : gameView fullscreen, gamepads par-dessus (overlay)
        buildLandscapeConstraints()
    } else {
        // Portrait : gameView en haut, gamepads en bas
        buildPortraitConstraints()
    }
    
    // NO Lemuroid theme needed - RetroArch overlays only!
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (layoutVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    // Mode RetroArch : Overlay fullscreen par-dessus le gameView
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Emulator View avec offset vertical pour éviter que les doigts cachent l'écran
                        // Portrait : offset de 20% vers le haut (laisse espace pour les doigts en bas)
                        // Landscape : légèrement remonté pour compenser la QuickActionsBar (si visible)
                        // AJOUT: Compenser la hauteur de la QuickActionsBar (40dp cutout + 36dp bar = ~76dp)
                        val quickBarOffsetDp = if (quickActionsBarVisible) -23.dp else 0.dp  // Remonter de ~23dp si visible
                        
                        val verticalOffsetDp = if (isLandscape) {
                            quickBarOffsetDp  // Landscape : remonter pour QuickBar (si visible)
                        } else {
                            quickBarOffsetDp + (-configuration.screenHeightDp * 0.20f).dp  // Portrait : 20% + QuickBar (si visible)
                        }
                        
                        AndroidView(
                            factory = { retroView },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .offset(y = verticalOffsetDp)
                                .onGloballyPositioned { layoutCoordinates ->
                                    // Capturer position ET bounds pour tenir compte de l'offset
                                    val position = layoutCoordinates.positionInWindow()
                                    val size = layoutCoordinates.size
                                    
                                    // Créer bounds réels incluant l'offset
                                    val realBounds = androidx.compose.ui.geometry.Rect(
                                        left = position.x,
                                        top = position.y,
                                        right = position.x + size.width,
                                        bottom = position.y + size.height
                                    )
                                    
                                    // Ne logger que si les bounds ont changé significativement (> 1dp)
                                    val oldBounds = gameViewBounds.value
                                    val hasChanged = oldBounds == null || 
                                        kotlin.math.abs(oldBounds.left - realBounds.left) > 3 ||
                                        kotlin.math.abs(oldBounds.top - realBounds.top) > 3 ||
                                        kotlin.math.abs(oldBounds.width - realBounds.width) > 3 ||
                                        kotlin.math.abs(oldBounds.height - realBounds.height) > 3
                                    
                                    if (hasChanged) {
                                        android.util.Log.d("ComposeEmulator", "[BOUNDS] GLRetroView REAL bounds: left=${realBounds.left}, top=${realBounds.top}, right=${realBounds.right}, bottom=${realBounds.bottom}, size=${realBounds.width}x${realBounds.height}")
                                    }
                                    
                                    gameViewBounds.value = realBounds
                                }
                                .pointerInteropFilter { event ->
                                    // Gérer Zapper en background (seulement pour touch hors overlay)
                                    // L'overlay au-dessus intercepte les touch sur boutons AVANT que ceci soit appelé
                                    // Donc ce code est appelé SEULEMENT pour touch hors boutons
                                    
                                    // FIX CRITIQUE: pointerInteropFilter ne transmet PAS tous les ACTION_MOVE!
                                    // On doit FORCER l'envoi continu pendant le touch
                                    if (isZapperGame) {
                                        // Appeler handleZapperTouch pour TOUS les événements (DOWN, MOVE, UP)
                                        val handled = onZapperTouch(event)
                                        
                                        // Si c'est un MOVE, logger pour debug
                                        if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                                            android.util.Log.v("RetroArchEmulator", "[ZAPPER] ACTION_MOVE received: (${event.x}, ${event.y})")
                                        }
                                        
                                        handled  // true si handled, false sinon
                                    } else {
                                        false  // Pas de Zapper, laisser passer
                                    }
                                }
                        )
                        
                        // ZapperCrosshair: Réticule personnalisé RetroPlay (si jeu Zapper et mode actif)
                        if (isZapperGame && crosshairMode.showRetroPlayCrosshair()) {
                            ZapperCrosshair(
                                onTouch = { event ->
                                    onZapperTouch(event)
                                },
                                visible = crosshairMode.showRetroPlayCrosshair()
                            )
                        }
                        
                        // Overlay RetroArch fullscreen par-dessus (appelé directement, pas via LayoutPair)
                        // Utiliser State pour recharger dynamiquement quand les prefs changent
                        val overlayPreferenceState = remember { mutableStateOf(com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console)) }
                        
                        // Utiliser State pour recharger dynamiquement les advanced settings
                        val advancedSettingsState = remember { mutableStateOf(com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console)) }
                        
                        // CRITIQUE: Garder une référence forte au listener pour éviter le garbage collection
                        val preferenceListener = remember {
                            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                android.util.Log.d("ComposeEmulator", "⚙️ Preference changed: key='$key' | console='$console'")
                                val matchesOverlay = key?.startsWith("overlay_$console") == true
                                val matchesVariant = key == "gamepad_${console}_variant"
                                val matchesDebug = key == "overlay_debug_mode"
                                // Détecter les advanced settings (sensibilité, opacity, recenter, etc.)
                                val matchesAdvanced = key?.startsWith("overlay_${console}_dpad_diagonal_sensitivity") == true ||
                                                    key?.startsWith("overlay_${console}_abxy_diagonal_sensitivity") == true ||
                                                    key?.startsWith("overlay_${console}_analog_recenter_zone") == true ||
                                                    key?.startsWith("overlay_${console}_opacity") == true ||
                                                    key?.startsWith("overlay_${console}_aspect_adjust") == true ||
                                                    key?.startsWith("overlay_${console}_hide_in_menu") == true ||
                                                    key?.startsWith("overlay_${console}_behind_menu") == true ||
                                                    key?.startsWith("overlay_${console}_hide_when_gamepad") == true ||
                                                    key?.startsWith("overlay_${console}_show_inputs") == true ||
                                                    key?.startsWith("overlay_${console}_show_inputs_port") == true ||
                                                    key?.startsWith("overlay_${console}_lightgun") == true ||
                                                    key?.startsWith("overlay_${console}_mouse") == true
                                
                                android.util.Log.d("ComposeEmulator", "  matchesOverlay=$matchesOverlay, matchesVariant=$matchesVariant, matchesDebug=$matchesDebug, matchesAdvanced=$matchesAdvanced")
                                
                                if (matchesOverlay || matchesVariant) {
                                    val newPref = com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console)
                                    overlayPreferenceState.value = newPref
                                    android.util.Log.i("ComposeEmulator", "🔄 Overlay preference reloaded for $console: overlay='${newPref?.overlayName}' landscape='${newPref?.landscapeLayout}' portrait='${newPref?.portraitLayout}'")
                                    // Reset currentRetroArchLayout pour forcer l'utilisation de la nouvelle préférence
                                    currentRetroArchLayout = null
                                }
                                
                                if (matchesDebug) {
                                    debugModeState.value = prefs.getBoolean("overlay_debug_mode", false)
                                    android.util.Log.d("ComposeEmulator", "Debug mode changed: ${debugModeState.value}")
                                }
                                
                                if (matchesAdvanced) {
                                    val newAdvanced = com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console)
                                    advancedSettingsState.value = newAdvanced
                                    android.util.Log.i("ComposeEmulator", "🔄 Advanced settings reloaded: dpadSens=${newAdvanced.dpadDiagonalSensitivity} abxySens=${newAdvanced.abxyDiagonalSensitivity} recenter=${newAdvanced.analogRecenterZone} opacity=${newAdvanced.opacity}")
                                }
                            }
                        }
                        
                        // Recharger la préférence quand elle change dans GamePad Settings
                        DisposableEffect(console) {
                            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
                            android.util.Log.d("ComposeEmulator", "Listener registered for $console")
                            onDispose {
                                prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                                android.util.Log.d("ComposeEmulator", "Listener unregistered for $console")
                            }
                        }
                        
                        val overlayPreference = overlayPreferenceState.value
                        if (overlayPreference != null) {
                            val assetManager = remember { com.retroplay.overlay.assets.OverlayAssetManager(retroView.context) }
                            // Recharger la config si le nom de l'overlay change
                            val overlayConfig = remember(overlayPreference.overlayName, overlayPreference.customCfgName, console) {
                                assetManager.loadOverlayConfig(overlayPreference.overlayName, console, overlayPreference.customCfgName)
                            }
                            
                            // Utiliser currentRetroArchLayout si défini (boutons overlay_next), 
                            // sinon utiliser la préférence (GamePad Settings)
                            // IMPORTANT: Dépendre aussi de overlayPreference.landscapeLayout et portraitLayout pour recomposer !
                            val requestedLayoutName = currentRetroArchLayout ?: run {
                                if (overlayPreference.autoRotate) {
                                    val selected = if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
                                    android.util.Log.d("ComposeEmulator", "Auto-rotate: isLandscape=$isLandscape, selected layout='$selected'")
                                    selected
                                } else {
                                    // Si pas auto-rotate, utiliser le layout selon l'orientation actuelle
                                    val selected = if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
                                    android.util.Log.d("ComposeEmulator", "Manual mode: isLandscape=$isLandscape, selected layout='$selected'")
                                    selected
                                }
                            }
                            
                            // Trouver le layout (avec fallback si le nom exact n'existe pas)
                            val layoutName = overlayConfig?.layouts?.get(requestedLayoutName)?.let { 
                                android.util.Log.d("ComposeEmulator", "Using requested layout: '$requestedLayoutName'")
                                requestedLayoutName 
                            }
                                ?: run {
                                    // Fallback : chercher le premier layout correspondant à l'orientation
                                    val orientation = if (isLandscape) "landscape" else "portrait"
                                    val fallback = overlayConfig?.layouts?.keys?.firstOrNull { it.contains(orientation) }
                                    android.util.Log.w("ComposeEmulator", "Layout '$requestedLayoutName' not found, using fallback: '$fallback'")
                                    fallback
                                }
                            
                            if (layoutName != null) {
                                overlayConfig?.layouts?.get(layoutName)?.let { overlayLayout ->
                                
                                // Utiliser advancedSettingsState pour rechargement dynamique
                                val advancedSettings = advancedSettingsState.value
                                
                                // Vérifier si un menu est ouvert (INCLURE Core Options Dialog!)
                                val isMenuOpen = showMainMenu.value || showQuickMenu.value || showGamePadSettings.value || showAdvancedOverlaySettings.value || showCoreOptionsDialog.value || showPerGameConfigDialog.value || showDiskSwapperDialog.value
                                
                                // Logique hideInMenu et behindMenu (RetroArch officiel)
                                val shouldShowOverlay = when {
                                    !overlaysVisible.value -> false  // Overlay désactivé manuellement
                                    !isMenuOpen -> true  // Pas de menu ouvert → afficher
                                    advancedSettings.hideInMenu -> false  // Menu ouvert + hideInMenu=true → cacher
                                    // Si on arrive ici: menu ouvert + hideInMenu=false
                                    // behindMenu détermine le Z-order (pas implémenté visuellement, mais on affiche)
                                    else -> true
                                }
                                
                                // key() force le recompose quand layoutName OU orientation change
                                // Afficher selon la logique hideInMenu/behindMenu
                                if (shouldShowOverlay) {
                                    androidx.compose.runtime.key(layoutName, isLandscape) {
                                        com.retroplay.overlay.renderer.RetroArchOverlayScreen(
                                        layout = overlayLayout,
                                        overlayName = overlayPreference.overlayName,
                                        assetManager = assetManager,
                                        showDebug = debugModeState.value,
                                        swapAnalogSticks = overlayPreference.swapAnalogSticks,
                                        invertAnalogY = overlayPreference.invertAnalogY,
                                        overlayScale = overlayPreference.scale,
                                        overlayXOffset = overlayPreference.xOffset,
                                        overlayYOffset = overlayPreference.yOffset,
                                        overlayXSeparation = overlayPreference.xSeparation,
                                        overlayYSeparation = overlayPreference.ySeparation,
                                        overlayOpacity = advancedSettings.opacity,
                                        dpadDiagonalSensitivity = advancedSettings.dpadDiagonalSensitivity,
                                        abxyDiagonalSensitivity = advancedSettings.abxyDiagonalSensitivity,
                                        showInputsMode = advancedSettings.showInputs,
                                        hideWhenGamepadConnected = advancedSettings.hideWhenGamepadConnected,
                                        analogRecenterZone = advancedSettings.analogRecenterZone,
                                        isZapperGame = isZapperGame,  // Passer le flag Zapper!
                                        onButtonPress = { action ->
                                            val keyCodes = com.retroplay.overlay.models.RetroArchButtonMapping.parseAction(action)
                                            if (keyCodes.isNotEmpty()) {
                                                Log.d("ComposeEmulator", "Buttons pressed: $action -> $keyCodes")
                                                keyCodes.forEach { keyCode ->
                                                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
                                                }
                                            }
                                        },
                                        onButtonRelease = { action ->
                                            val keyCodes = com.retroplay.overlay.models.RetroArchButtonMapping.parseAction(action)
                                            if (keyCodes.isNotEmpty()) {
                                                Log.d("ComposeEmulator", "Buttons released: $action -> $keyCodes")
                                                keyCodes.forEach { keyCode ->
                                                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, keyCode, 0)
                                                }
                                            }
                                        },
                                        onLayoutSwitch = { requestedLayout ->
                                            // Mapper le layout demandé à l'orientation physique du device
                                            val mappedLayout = mapLayoutToDeviceOrientation(
                                                requestedLayout = requestedLayout,
                                                isLandscape = isLandscape,
                                                availableLayouts = overlayConfig.layouts.keys
                                            )
                                            currentRetroArchLayout = mappedLayout
                                            Log.i("ComposeEmulator", "RetroArch layout switch: $layoutName -> requested='$requestedLayout' mapped='$mappedLayout' (device=${if (isLandscape) "landscape" else "portrait"})")
                                        },
                                        onMenuToggle = {
                                            Log.i("ComposeEmulator", "Menu toggle from RetroArch overlay")
                                            showMainMenu.value = true
                                        },
                                        onAnalogMove = { action, x, y ->
                                            // Envoyer les valeurs analog à l'émulateur
                                            val source = when (action) {
                                                "analog_left" -> com.swordfish.libretrodroid.GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                                                "analog_right" -> com.swordfish.libretrodroid.GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                                                else -> com.swordfish.libretrodroid.GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                                            }
                                            // Envoyer directement sans inversion (comme Lemuroid)
                                            Log.d("AnalogInput", "sendMotionEvent: source=$source, x=$x, y=$y")
                                            retroView.sendMotionEvent(source, x, y)
                                        },
                                        onHotkey = onHotkey,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                                }
                            } else {
                                // Layout non trouvé - Afficher message d'erreur
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            "Layout '$requestedLayoutName' not found in overlay",
                                            color = Color(0xFFFF5722),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Available layouts: ${overlayConfig?.layouts?.keys?.joinToString()}",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        androidx.compose.material3.Button(
                                            onClick = { showMainMenu.value = true },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF9800)
                                            )
                                        ) {
                                            Text("Open Settings", color = Color.Black)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Aucun overlay chargé - Afficher message d'erreur
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        "No RetroArch overlay loaded",
                                        color = Color(0xFFFF5722),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        "Please select an overlay in GamePad Settings\nor switch back to Default mode",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    androidx.compose.material3.Button(
                                        onClick = { showMainMenu.value = true },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF9800)
                                        )
                                    ) {
                                        Text("Open Settings", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
                // NO ELSE - RetroArch mode ONLY in this activity!
                
                // Quick Actions Bar (Hybrid mode - variante F)
                if (quickActionsBarVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        com.retroplay.ui.QuickActionsBar(
                            isFastForwardActive = isFastForwardActive,
                            audioMuted = audioMuted,
                            onToggleFastForward = onToggleFastForward,
                            onToggleAudioMute = onToggleAudioMute,
                            onQuickSave = { onSaveState(1) },  // Quick save slot 1
                            onQuickLoad = { onLoadState(1) },  // Quick load slot 1
                            onCycleShader = onCycleShader,  // Quick Win #4
                            currentShaderName = currentShaderName,
                            onOpenSettings = { showMainMenu.value = true }
                        )
                    }
                }
                
                // États locaux pour les sous-menus
                var showSaveSlots by remember { mutableStateOf(false) }
                var showLoadSlots by remember { mutableStateOf(false) }
                var showCheatCodes by remember { mutableStateOf(false) }
                var showCoreSelector by remember { mutableStateOf(false) }
                var showRestartDialog by remember { mutableStateOf(false) }
                var selectedCoreForRestart by remember { mutableStateOf<String?>(null) }
                
                // Capturer le context Activity pour l'utiliser dans les lambdas
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? android.app.Activity
                
                // Quick Menu (Menu Rapide - Back button)
                if (showQuickMenu.value) {
                    QuickMenuDialog(
                        onDismiss = { 
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                        },
                        onHideOverlay = {
                            overlaysVisible.value = !overlaysVisible.value
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                        },
                        onSettings = {
                            showQuickMenu.value = false
                            showMainMenu.value = true
                        },
                        onAdvancedSettings = {
                            showQuickMenu.value = false
                            showAdvancedOverlaySettings.value = true
                        },
                        onGameInfo = {
                            showQuickMenu.value = false
                            showGameInfoDialog.value = true
                        },
                        onCheats = {
                            showQuickMenu.value = false
                            showCheatsDialog.value = true
                        },
                        onSaveState = { slot ->
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                            onSaveState(slot)
                        },
                        onLoadState = { slot ->
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                            onLoadState(slot)
                        },
                        onQuit = {
                            showQuickMenu.value = false
                            retroView.onPause()
                            onFinishActivity()
                        },
                        onToggleFastForward = {
                            onToggleFastForward()
                            closeQuickMenuWithCooldown()
                        },
                        onToggleAudioMute = {
                            onToggleAudioMute()
                        },
                        onCycleShader = {
                            onCycleShader()
                        },
                        onToggleQuickActionsBar = {
                            onToggleQuickActionsBar()
                        },
                        onConfigureZapper = {
                            onConfigureZapper()  // TEST: Configure Zapper et redémarre ROM
                            closeQuickMenuWithCooldown()
                        },
                        onToggleCrosshairMode = {
                            onToggleCrosshairMode()  // Cycle crosshair mode
                        },
                        overlaysVisible = overlaysVisible.value,
                        isFastForwardActive = isFastForwardActive,
                        audioMuted = audioMuted,
                        currentShaderName = currentShaderName,
                        quickActionsBarVisible = quickActionsBarVisible,
                        isZapperGame = isZapperGame,  // CRITICAL: Afficher bouton Configure Zapper
                        crosshairMode = crosshairMode,  // Mode d'affichage du crosshair
                        hasGameInfo = (gameCRC != null),  // Database info available
                        hasCheats = loadedCheats.isNotEmpty()  // Cheats available
                    )
                }
                
                // Main Menu (Save/Load/Settings/Cheats)
                if (showMainMenu.value) {
                    MainMenuDialog(
                        gameName = gameName,
                        console = console,
                        prefs = prefs,
                        onDismiss = { showMainMenu.value = false },
                        onSaveGame = { 
                            showMainMenu.value = false
                            showSaveSlots = true
                        },
                        onLoadGame = { 
                            showMainMenu.value = false
                            showLoadSlots = true
                        },
                        onGamePadSettings = {
                            showMainMenu.value = false
                            showGamePadSettings.value = true
                        },
                        onAdvancedSettings = {
                            showMainMenu.value = false
                            showAdvancedOverlaySettings.value = true
                        },
                        onCheatCodes = {
                            showMainMenu.value = false
                            showCheatCodes = true
                        },
                        onChangeCore = {
                            showMainMenu.value = false
                            showCoreSelector = true
                        },
                        onDipSwitches = {
                            showMainMenu.value = false
                            showDipSwitchDialog.value = true
                        },
                        onCoreOptions = {
                            showMainMenu.value = false
                            showCoreOptionsDialog.value = true
                        },
                        onSmartConfig = {
                            showMainMenu.value = false
                            showSmartConfigDialog.value = true
                        },
                        onDiskSwapper = {
                            showMainMenu.value = false
                            showDiskSwapperDialog.value = true
                        },
                        onScreenshot = {
                            showMainMenu.value = false
                            onTakeScreenshot()
                        },
                        hasDipSwitches = dipSwitches.isNotEmpty(),
                        hasCoreOptions = coreOptions.isNotEmpty(),
                        availableDisks = availableDisks
                    )
                }
                
                // Save Slots Dialog
                if (showSaveSlots) {
                    SlotSelectionDialog(
                        title = "Save Game",
                        console = console,
                        gameName = gameName,
                        onDismiss = { showSaveSlots = false },
                        onSlotSelected = { slot ->
                            showSaveSlots = false
                            onSaveState(slot)
                        }
                    )
                }
                
                // Load Slots Dialog
                if (showLoadSlots) {
                    SlotSelectionDialog(
                        title = "Load Game",
                        console = console,
                        gameName = gameName,
                        onDismiss = { showLoadSlots = false },
                        onSlotSelected = { slot ->
                            showLoadSlots = false
                            onLoadState(slot)
                        }
                    )
                }
                
                // RetroArch Settings Dialog (simplified - NO Radial gamepad settings)
                if (showGamePadSettings.value) {
                    RetroArchSettingsDialog(
                        console = console,
                        onDismiss = { showGamePadSettings.value = false },
                        context = retroView.context,
                        prefs = prefs,
                        onLoadCustomCfg = onLoadCustomCfg,
                        debugModeState = debugModeState
                    )
                }
                
                // Advanced Overlay Settings Dialog
                if (showAdvancedOverlaySettings.value) {
                    AdvancedOverlaySettingsDialog(
                        console = console,
                        onDismiss = { showAdvancedOverlaySettings.value = false },
                        context = retroView.context,
                        prefs = prefs
                    )
                }
                
                // Cheat Codes Dialog
                if (showCheatCodes) {
                    val cheatManager = remember { com.retroplay.cheat.CheatManager(retroView.context) }
                    
                    // RECHARGER les codes à chaque ouverture pour avoir l'état actuel
                    // IMPORTANT: Passer romPath pour la recherche dans sous-répertoires
                    var cheats by remember(showCheatCodes) { 
                        mutableStateOf(cheatManager.loadCheatsForGame(console, gameName, romPath)) 
                    }
                    
                    var showAddCheatDialog by remember { mutableStateOf(false) }
                    
                    com.retroplay.cheat.CheatSelectionDialog(
                        gameName = gameName,
                        console = console,
                        cheats = cheats,
                        onDismiss = { showCheatCodes = false },
                        onCheatsChanged = { updatedCheats ->
                            cheats = updatedCheats
                            // Sauvegarder les modifications
                            cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
                            // Appliquer immédiatement au core
                            cheatApplier.applyCheatsList(updatedCheats)
                            Log.i("NativeComposeEmulator", "[$console] Applied ${updatedCheats.count { it.enabled }} active cheat(s)")
                        },
                        onAddCustomCheat = {
                            showAddCheatDialog = true
                        },
                        onDeleteCheat = { deletedCheat ->
                            // Supprimer du fichier .cht custom
                            cheatManager.deleteCustomCheat(console, gameName, deletedCheat)
                            // Recharger tous les codes
                            cheats = cheatManager.loadCheatsForGame(console, gameName)
                        }
                    )
                    
                    // Dialog pour ajouter un code personnalisé
                    if (showAddCheatDialog) {
                        com.retroplay.cheat.AddCustomCheatDialog(
                            console = console,
                            onDismiss = { showAddCheatDialog = false },
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
                
                // Core Selector Dialog
                if (showCoreSelector) {
                    CoreSelectorDialog(
                        console = console,
                        currentGamePath = romPath,
                        onCoreSelected = { selectedCore ->
                            showCoreSelector = false
                            // Save override and show restart dialog
                            CoreSelector.setCoreOverride(
                                romPath,
                                selectedCore.coreId,
                                "User selected from Main Menu: ${selectedCore.displayName}"
                            )
                            selectedCoreForRestart = selectedCore.displayName
                            showRestartDialog = true
                        },
                        onResetToDefault = {
                            showCoreSelector = false
                            // Remove override and show restart dialog
                            CoreSelector.removeCoreOverride(romPath)
                            selectedCoreForRestart = null
                            showRestartDialog = true
                        },
                        onDismiss = {
                            showCoreSelector = false
                        }
                    )
                }
                
                // Restart Confirmation Dialog
                if (showRestartDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showRestartDialog = false },
                        title = {
                            Text(
                                text = "Core Changed",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = if (selectedCoreForRestart != null) {
                                    "Core changed to $selectedCoreForRestart.\n\nRelaunch the game from the menu to apply."
                                } else {
                                    "Core reset to default.\n\nRelaunch the game from the menu to apply."
                                },
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showRestartDialog = false
                                    // Fermer le jeu - l'utilisateur relancera depuis GameDetailsActivity
                                    onFinishActivity()
                                }
                            ) {
                                Text("OK", color = Color(0xFF4CAF50))
                            }
                        },
                        containerColor = Color(0xFF2C2C2C),
                        tonalElevation = 8.dp
                    )
                }
                
                // ZapperBox supprimé - Zapper géré en background sur AndroidView (ne bloque plus les overlays)
        }
    }
}

// Build ConstraintSet pour mode Portrait
private fun buildPortraitConstraints(): ConstraintSet {
    return ConstraintSet {
        val gameView = createRefFor("gameView")
        val leftPad = createRefFor("leftPad")
        val rightPad = createRefFor("rightPad")
        
        val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)
        
        constrain(gameView) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(leftPad.top)  // gameView s'arrête au-dessus des gamepads
        }
        
        constrain(gamePadChain) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
        
        constrain(leftPad) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom)
        }
        
        constrain(rightPad) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom)
        }
    }
}

// Build ConstraintSet pour mode Paysage
private fun buildLandscapeConstraints(): ConstraintSet {
    return ConstraintSet {
        val gameView = createRefFor("gameView")
        val leftPad = createRefFor("leftPad")
        val rightPad = createRefFor("rightPad")
        
        val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)
        
        constrain(gameView) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(parent.bottom)  // Fullscreen
        }
        
        constrain(gamePadChain) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
        
        constrain(leftPad) {
            width = Dimension.wrapContent
            height = Dimension.matchParent
            bottom.linkTo(parent.bottom)
        }
        
        constrain(rightPad) {
            width = Dimension.wrapContent
            height = Dimension.matchParent
            bottom.linkTo(parent.bottom)
        }
    }
}

// Main Menu Dialog (Save/Load/Settings/Cheats/Cache)
@Composable
private fun MainMenuDialog(
    gameName: String,
    console: String,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onGamePadSettings: () -> Unit,
    onAdvancedSettings: () -> Unit = {},  // Nouveau callback
    onCheatCodes: () -> Unit,
    onChangeCore: () -> Unit,
    onDipSwitches: () -> Unit,
    onCoreOptions: () -> Unit,
    onSmartConfig: () -> Unit = {},  // Smart Config callback
    onDiskSwapper: () -> Unit = {},  // Disk Swapper callback
    onScreenshot: () -> Unit = {},  // Screenshot callback
    hasDipSwitches: Boolean,
    hasCoreOptions: Boolean,
    availableDisks: Int = 0  // Number of available disks (multi-disc support)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cacheState by remember { mutableStateOf(prefs.getBoolean("cache_enabled_$console", false)) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Titre
                    Text(
                        gameName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Save Game
                    TextButton(
                        onClick = onSaveGame,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Game", color = Color.White)
                    }
                    
                    // Load Game
                    TextButton(
                        onClick = onLoadGame,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Game", color = Color.White)
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Screenshot
                    TextButton(
                        onClick = onScreenshot,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Take Screenshot", color = Color(0xFF2196F3))
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Cheat Codes
                    TextButton(
                        onClick = onCheatCodes,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cheat Codes", color = Color(0xFF4CAF50))
                    }
                    
                    // GamePad Settings
                    TextButton(
                        onClick = onGamePadSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GamePad Settings", color = Color.White)
                    }
                    
                    // DIP Switches (arcade only, if available)
                    if (hasDipSwitches) {
                        TextButton(
                            onClick = onDipSwitches,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DIP Switches", color = Color(0xFFFFA726))
                        }
                    }
                    
                    // Core Options (if available)
                    if (hasCoreOptions) {
                        TextButton(
                            onClick = onCoreOptions,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Core Options", color = Color(0xFF64B5F6))
                        }
                    }
                    
                    // Advanced Overlay Settings
                    TextButton(
                        onClick = onAdvancedSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Advanced Overlay Settings", color = Color(0xFFFF9800))
                    }
                    
                    // Smart Config Settings
                    TextButton(
                        onClick = onSmartConfig,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💡 Smart Config", color = Color(0xFF4CAF50))
                    }
                    
                    // Disk Swapper (PSX multi-disc games)
                    if (availableDisks > 1) {
                        TextButton(
                            onClick = onDiskSwapper,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Swap Disk ($availableDisks discs)", color = Color(0xFFFF9800))
                        }
                    }
                    
                    // Change Core
                    TextButton(
                        onClick = onChangeCore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Core & Restart", color = Color(0xFFE91E63))
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // ZIP Cache Toggle (pour toutes les consoles)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ZIP Cache Extraction", color = Color.White)
                            Text(
                                "Enable if ROM doesn't load",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = cacheState,
                            onCheckedChange = { enabled ->
                                cacheState = enabled
                                prefs.edit().putBoolean("cache_enabled_$console", enabled).apply()
                                android.util.Log.i("MainMenu", "[$console] Cache enabled: $enabled")
                                android.widget.Toast.makeText(context,
                                    "[$console] Cache " + (if (enabled) "enabled" else "disabled"), 
                                    android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Close
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

// Slot Selection Dialog (Save/Load avec 5 slots par console)
@Composable
private fun SlotSelectionDialog(
    title: String,
    console: String,
    gameName: String,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)  // Pleine largeur (95% pour petites marges)
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Titre avec console
                    Text(
                        "$title - ${console.uppercase()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    
                    Text(
                        gameName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // 5 slots avec infos détaillées
                    for (slot in 1..5) {
                        val saveFile = java.io.File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$slot/${gameName}.state")
                        val isOccupied = saveFile.exists()
                        
                        TextButton(
                            onClick = { onSlotSelected(slot) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Ligne 1 : Slot + Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Slot $slot",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    
                                    if (isOccupied) {
                                        Text(
                                            "[Occupied]",
                                            color = Color(0xFF4CAF50),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        Text(
                                            "[Empty]",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                
                                // Ligne 2 : Infos détaillées (si occupé)
                                if (isOccupied) {
                                    val lastModified = saveFile.lastModified()
                                    val sizeKB = saveFile.length() / 1024
                                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    val dateStr = dateFormat.format(java.util.Date(lastModified))
                                    
                                    Text(
                                        "$dateStr - ${sizeKB}KB",
                                        color = Color(0xFFAAAAAA),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Cancel
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }
    }
}

// GamePadSettingsDialog REMOVED - Not needed for RetroArch-only activity
// TODO: Create RetroArchSettingsDialog for RetroArch-specific overlay configuration

/*
 * OLD GamePadSettingsDialog CODE COMMENTED OUT - TO BE REMOVED
    var marginX by remember { mutableFloatStateOf(currentSettings.marginX) }
    var marginY by remember { mutableFloatStateOf(currentSettings.marginY) }
    var selectedVariant by remember { mutableStateOf(currentVariant) }
    
    // État pour l'overlay RetroArch
    val assetManager = remember { com.retroplay.overlay.assets.OverlayAssetManager(context) }
    val availableOverlays = remember { assetManager.getCompatibleOverlays(console) }
    
    // Charger préférence actuelle
    val currentOverlayPref = remember { com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console) }
    var selectedOverlay by remember { mutableStateOf(currentOverlayPref?.overlayName ?: if (availableOverlays.isNotEmpty()) availableOverlays[0] else "") }
    var selectedLandscapeLayout by remember { mutableStateOf(currentOverlayPref?.landscapeLayout ?: "landscape-A") }
    var selectedPortraitLayout by remember { mutableStateOf(currentOverlayPref?.portraitLayout ?: "portrait-A") }
    var autoRotate by remember { mutableStateOf(currentOverlayPref?.autoRotate ?: true) }
    
    // État pour détecter si un slider est en train d'être bougé
    var isAdjusting by remember { mutableStateOf(false) }
    
    // Liste des variantes disponibles pour cette console
    val availableVariants = GamePadLayoutManager.getAvailableVariants(console)
    
    // Charger layouts disponibles pour l'overlay sélectionné (si RetroArch)
    val availableLayouts = remember(selectedOverlay) {
        if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH && selectedOverlay.isNotEmpty()) {
            val layouts = assetManager.getAvailableLayouts(selectedOverlay)
            android.util.Log.d("GamePadSettings", "Loaded ${layouts.size} layouts for '$selectedOverlay': ${layouts.joinToString()}")
            layouts
        } else {
            emptyList()
        }
    }
    
    // Sauvegarder la préférence overlay quand modifiée
    LaunchedEffect(selectedVariant, selectedOverlay, selectedLandscapeLayout, selectedPortraitLayout, autoRotate) {
        if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH && selectedOverlay.isNotEmpty()) {
            val pref = com.retroplay.overlay.models.OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate
            )
            com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, pref)
            android.util.Log.i("GamePadSettings", "✅ SAVED overlay pref for $console: overlay='$selectedOverlay' landscape='$selectedLandscapeLayout' portrait='$selectedPortraitLayout' autoRotate=$autoRotate")
        } else if (selectedVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) {
            // Désactiver RetroArch si autre variante choisie
            com.retroplay.overlay.models.OverlayPreferenceManager.disable(prefs, console)
        }
    }
    
    // Alpha du fond : plus transparent quand on ajuste
    val dialogAlpha = if (isAdjusting) 0x33000000 else 0x99000000
    
    // LIVE PREVIEW : Appliquer les changements instantanément
    LaunchedEffect(scale, rotation, marginX, marginY) {
        onApply(
            TouchControllerSettingsManager.Settings(
                scale = scale,
                rotation = rotation,
                marginX = marginX,
                marginY = marginY
            )
        )
    }
    
    // Appliquer changement de variante
    LaunchedEffect(selectedVariant) {
        if (selectedVariant != currentVariant) {
            onVariantChange(selectedVariant)
        }
    }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter  // Positionner en haut
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.8f)  // Limiter hauteur à 80% de l'écran
                    .padding(top = 40.dp),
                colors = CardDefaults.cardColors(containerColor = Color(dialogAlpha))
            ) {
            // Ajouter ScrollView pour le contenu qui peut être long
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Titre
                Text(
                    "GamePad Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                
                // Sélecteur de variante (si plusieurs disponibles)
                if (availableVariants.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("Gamepad Mode", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableVariants.forEach { (variant, label) ->
                            val isSelected = selectedVariant == variant
                            TextButton(
                                onClick = { selectedVariant = variant },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) Color(0xFF4CAF50) else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Configuration RetroArch Overlay (affiché seulement si variante RETROARCH sélectionnée)
                if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("RetroArch Overlay", color = Color(0xFFFF9800), style = MaterialTheme.typography.titleMedium)
                    
                    // Sélection de l'overlay package
                    if (availableOverlays.isNotEmpty()) {
                        Text("Overlay Package", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Column {
                            availableOverlays.forEach { overlayName ->
                                val isSelected = selectedOverlay == overlayName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOverlay = overlayName }
                                        .background(if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.Transparent)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedOverlay = overlayName }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        overlayName,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        // Sélection des layouts (si overlay chargé)
                        if (selectedOverlay.isNotEmpty() && availableLayouts.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            
                            // Filtrer par orientation
                            val landscapeLayouts = availableLayouts.filter { it.contains("landscape") }
                            val portraitLayouts = availableLayouts.filter { it.contains("portrait") }
                            
                            if (landscapeLayouts.isNotEmpty()) {
                                Text("Landscape Layout", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                // Afficher tous les layouts en plusieurs lignes si nécessaire
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    landscapeLayouts.forEach { layoutName ->
                                        val isSelected = selectedLandscapeLayout == layoutName
                                        // Extraire label lisible (ex: "landscape-left-analog" -> "Left Analog")
                                        val displayName = when {
                                            layoutName.contains("both-analog") -> "Both Analog"
                                            layoutName.contains("left-analog") && layoutName.contains("menu") -> "L.Analog+Menu"
                                            layoutName.contains("left-analog") -> "Left Analog"
                                            layoutName.contains("right-analog") -> "Right Analog"
                                            layoutName.contains("analog") && layoutName.contains("menu") -> "Analog+Menu"
                                            layoutName.contains("analog") -> "Analog"
                                            layoutName.contains("menu") -> "Menu"
                                            layoutName.endsWith("-B") -> "B"
                                            else -> "Digital"
                                        }
                                        TextButton(
                                            onClick = { 
                                                android.util.Log.i("GamePadSettings", "Landscape layout changed: $selectedLandscapeLayout -> $layoutName")
                                                selectedLandscapeLayout = layoutName
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                                            ),
                                            modifier = Modifier.padding(0.dp)
                                        ) {
                                            Text(
                                                displayName,
                                                color = if (isSelected) Color.Black else Color.Gray,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (portraitLayouts.isNotEmpty()) {
                                Text("Portrait Layout", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                // Afficher tous les layouts en plusieurs lignes si nécessaire
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    portraitLayouts.forEach { layoutName ->
                                        val isSelected = selectedPortraitLayout == layoutName
                                        // Extraire label lisible
                                        val displayName = when {
                                            layoutName.contains("analog") && layoutName.contains("menu") -> "Analog+Menu"
                                            layoutName.contains("analog") -> "Analog"
                                            layoutName.contains("menu") -> "Menu"
                                            else -> "Digital"
                                        }
                                        TextButton(
                                            onClick = { 
                                                android.util.Log.i("GamePadSettings", "Portrait layout changed: $selectedPortraitLayout -> $layoutName")
                                                selectedPortraitLayout = layoutName
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                                            ),
                                            modifier = Modifier.padding(0.dp)
                                        ) {
                                            Text(
                                                displayName,
                                                color = if (isSelected) Color.Black else Color.Gray,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Auto-rotate option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = autoRotate,
                                    onCheckedChange = { autoRotate = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFFF9800),
                                        uncheckedColor = Color.Gray
                                    )
                                )
                                Text(
                                    "Auto-switch on rotation",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            // DEBUG MODE - Afficher les hitboxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var debugMode by remember { 
                                    mutableStateOf(prefs.getBoolean("overlay_debug_mode", false)) 
                                }
                                
                                androidx.compose.material3.Switch(
                                    checked = debugMode,
                                    onCheckedChange = { 
                                        debugMode = it
                                        prefs.edit().putBoolean("overlay_debug_mode", it).apply()
                                    },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFF5722),
                                        checkedTrackColor = Color(0xFFFF5722).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "DEBUG: Show hitboxes",
                                    color = if (debugMode) Color(0xFFFF5722) else Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Text(
                            "No compatible overlays found for $console",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                }
                
                // Sliders Lemuroid (affichés SEULEMENT si mode Default ou Compact)
                if (selectedVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    Text("Lemuroid Gamepad Adjustments", color = Color(0xFF2196F3), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    
                    // Scale (0.75x - 1.5x)
                    Text("Scale: ${String.format("%.2f", scale * 0.75f + 0.75f)}x", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = scale,
                        onValueChange = { 
                            scale = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Rotation (0° - 45°)
                    Text("Rotation: ${String.format("%.0f", rotation * 45f)}°", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = rotation,
                        onValueChange = { 
                            rotation = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Margin X (0dp - 96dp)
                    Text("Margin X: ${String.format("%.0f", marginX * 96f)}dp", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = marginX,
                        onValueChange = { 
                            marginX = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Margin Y (0dp - 96dp)
                    Text("Margin Y: ${String.format("%.0f", marginY * 96f)}dp", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = marginY,
                        onValueChange = { 
                            marginY = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(8.dp))
                }
                
                // Boutons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Bouton RESET TO DEFAULT (à gauche)
                    androidx.compose.material3.Button(
                        onClick = {
                            // Forcer le retour au mode DEFAULT
                            selectedVariant = GamePadLayoutManager.LayoutVariant.DEFAULT
                            // Désactiver RetroArch overlay
                            com.retroplay.overlay.models.OverlayPreferenceManager.disable(prefs, console)
                            // Appliquer le changement
                            onVariantChange(GamePadLayoutManager.LayoutVariant.DEFAULT)
                            android.util.Log.i("GamePadSettings", "Force reset to DEFAULT mode for $console")
                            onDismiss()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Text("RESET TO DEFAULT", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    
                    // Bouton Done (à droite)
                    androidx.compose.material3.Button(
                        onClick = onDismiss,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("DONE", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            }
        }
    }
}
*/

// Quick Menu Dialog (Menu Rapide - Bouton Back)
@Composable
private fun QuickMenuDialog(
    onDismiss: () -> Unit,
    onHideOverlay: () -> Unit,
    onSettings: () -> Unit,
    onAdvancedSettings: () -> Unit = {},  // Nouveau callback
    onGameInfo: () -> Unit = {},  // NEW: Show game database info
    onCheats: () -> Unit = {},  // NEW: Show cheats dialog
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onQuit: () -> Unit,
    onToggleFastForward: () -> Unit = {},  // Quick Win #1
    onToggleAudioMute: () -> Unit = {},    // Quick Win #2
    onCycleShader: () -> Unit = {},        // Quick Win #4
    onToggleQuickActionsBar: () -> Unit = {},  // QuickActionsBar visibility toggle
    onConfigureZapper: () -> Unit = {},    // TEST: Configure Zapper manuellement
    onToggleCrosshairMode: () -> Unit = {},  // Toggle Crosshair mode (RetroPlay/FCEUmm/Both/None)
    overlaysVisible: Boolean,
    isFastForwardActive: Boolean = false,
    audioMuted: Boolean = false,
    currentShaderName: String = "None",
    quickActionsBarVisible: Boolean = true,
    isZapperGame: Boolean = false,
    crosshairMode: CrosshairMode = CrosshairMode.RETROPLAY_ONLY,
    hasGameInfo: Boolean = false,  // NEW: If database info available
    hasCheats: Boolean = false  // NEW: If cheats are loaded
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(0.85f)
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // Titre
                Text(
                    "MENU RAPIDE",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                
                Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                
                // Bouton Resume (si en pause)
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("RESUME", color = Color.White)
                }
                
                // Bouton Hide/Show Overlay
                androidx.compose.material3.Button(
                    onClick = onHideOverlay,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text(
                        if (overlaysVisible) "HIDE OVERLAY" else "SHOW OVERLAY", 
                        color = Color.White
                    )
                }
                
                // Bouton Hide/Show QuickActionsBar
                androidx.compose.material3.Button(
                    onClick = onToggleQuickActionsBar,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (quickActionsBarVisible) Color(0xFF00BCD4) else Color(0xFF607D8B)
                    )
                ) {
                    Text(
                        if (quickActionsBarVisible) "QUICK BAR: VISIBLE" else "QUICK BAR: HIDDEN", 
                        color = Color.White
                    )
                }
                
                // Bouton Save State (Quick Save Slot 1)
                androidx.compose.material3.Button(
                    onClick = { onSaveState(1) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("SAVE STATE (Slot 1)", color = Color.White)
                }
                
                // Bouton Load State (Quick Load Slot 1)
                androidx.compose.material3.Button(
                    onClick = { onLoadState(1) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Text("LOAD STATE (Slot 1)", color = Color.White)
                }
                
                // Quick Win #1: Fast Forward Toggle
                androidx.compose.material3.Button(
                    onClick = onToggleFastForward,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isFastForwardActive) Color(0xFFFF5722) else Color(0xFF795548)
                    )
                ) {
                    Text(
                        if (isFastForwardActive) "FAST FORWARD: ON (2x)" else "FAST FORWARD: OFF",
                        color = Color.White
                    )
                }
                
                // Quick Win #2: Audio Mute Toggle
                androidx.compose.material3.Button(
                    onClick = onToggleAudioMute,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (audioMuted) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    )
                ) {
                    Text(
                        if (audioMuted) "AUDIO: MUTED" else "AUDIO: ON",
                        color = Color.White
                    )
                }
                
                // Bouton Game Info (si database info disponible)
                if (hasGameInfo) {
                    androidx.compose.material3.Button(
                        onClick = onGameInfo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0)
                        )
                    ) {
                        Text("📊 GAME INFO", color = Color.White)
                    }
                }
                
                // Bouton Cheats (si cheats disponibles)
                if (hasCheats) {
                    androidx.compose.material3.Button(
                        onClick = onCheats,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700)
                        )
                    ) {
                        Text("🎮 CHEATS", color = Color.Black)
                    }
                }
                
                // Bouton Settings (ouvrir le menu complet)
                androidx.compose.material3.Button(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF607D8B)
                    )
                ) {
                    Text("SETTINGS", color = Color.White)
                }
                
                Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                
                // TEST: Configure Zapper manuellement (si jeu Zapper)
                if (isZapperGame) {
                    androidx.compose.material3.Button(
                        onClick = onConfigureZapper,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text("CONFIGURE ZAPPER NOW", color = Color.White)
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // Toggle Crosshair Mode (pour debug / comparaison)
                    androidx.compose.material3.Button(
                        onClick = onToggleCrosshairMode,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = when (crosshairMode) {
                                CrosshairMode.RETROPLAY_ONLY -> Color(0xFF4CAF50)
                                CrosshairMode.FCEUMM_ONLY -> Color(0xFF2196F3)
                                CrosshairMode.BOTH -> Color(0xFFFF9800)
                                CrosshairMode.NONE -> Color(0xFF9E9E9E)
                            }
                        )
                    ) {
                        Text(
                            "CROSSHAIR: ${crosshairMode.displayName}",
                            color = Color.White
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                }
                
                // Bouton Quit
                androidx.compose.material3.Button(
                    onClick = onQuit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Text("QUIT GAME", color = Color.White)
                }
                }
            }
        }
    }
}

/**
 * Mappe un layout demandé vers un layout compatible avec l'orientation physique du device
 * 
 * RÈGLE SIMPLE : 
 * - Si le layout existe → L'utiliser tel quel (pas de mapping)
 * - Si le layout n'existe PAS → Chercher un équivalent dans l'orientation du device
 * 
 * @param requestedLayout Layout demandé par le bouton système
 * @param isLandscape True si le device est en landscape, false si portrait
 * @param availableLayouts Ensemble des layouts disponibles dans l'overlay actuel (optionnel)
 * @return Layout mappé compatible avec l'orientation physique
 */
private fun mapLayoutToDeviceOrientation(
    requestedLayout: String,
    isLandscape: Boolean,
    availableLayouts: Set<String>? = null
): String {
    // ÉTAPE 1 : Si le layout demandé existe, l'utiliser tel quel
    if (availableLayouts?.contains(requestedLayout) == true) {
        return requestedLayout
    }
    
    // ÉTAPE 2 : Le layout n'existe pas → Trouver un équivalent dans l'orientation du device
    val deviceOrientation = if (isLandscape) "landscape" else "portrait"
    
    // Déterminer le type de layout demandé
    val isAnalog = requestedLayout.contains("analog", ignoreCase = true)
    val isHidden = requestedLayout.contains("hidden", ignoreCase = true)
    
    // Chercher un équivalent compatible
    if (availableLayouts == null) {
        return requestedLayout  // Pas de layouts disponibles : retourner tel quel
    }
    
    return when {
        isHidden -> {
            // Layout hidden : chercher n'importe quel hidden
            availableLayouts.find { it.contains("hidden", ignoreCase = true) }
                ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
        isAnalog -> {
            // Layout analog : chercher un layout analog dans l'orientation du device
            availableLayouts.find { 
                it.contains(deviceOrientation, ignoreCase = true) && it.contains("analog", ignoreCase = true)
            } ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
        else -> {
            // Layout de base : chercher le layout de base dans l'orientation du device
            availableLayouts.find { 
                it == deviceOrientation || it == "$deviceOrientation-A"
            } ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
    }
}

/**
 * Trouve un layout de fallback pour une orientation donnée
 */
private fun findFallbackLayout(orientation: String, availableLayouts: Set<String>): String {
    // Chercher le premier layout correspondant à l'orientation
    return availableLayouts.firstOrNull { it.contains(orientation, ignoreCase = true) }
        ?: availableLayouts.firstOrNull() // Dernier recours : premier layout disponible
        ?: "landscape" // Ultra-fallback
}

@Composable
private fun CoreErrorDialog(
    coreName: String,
    gameName: String,
    onChangeCore: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Pas de dismiss en cliquant à l'extérieur */ },
        title = {
            Text(
                text = "Core Loading Failed",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "$coreName could not load:",
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = gameName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bouton "Change Core"
                TextButton(
                    onClick = onChangeCore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Core", color = Color(0xFFE91E63), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                // Bouton "Retry"
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry", color = Color(0xFF4CAF50), fontSize = 16.sp)
                }
                
                // Bouton "Cancel"
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color(0xFF9E9E9E), fontSize = 16.sp)
                }
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
