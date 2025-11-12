package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.retroplay.overlay.assets.OverlayAssetManager
import com.retroplay.overlay.models.OverlayPreferenceManager
import com.retroplay.overlay.models.RetroArchButtonMapping
import com.retroplay.overlay.renderer.RetroArchOverlayScreen
import com.swordfish.touchinput.radial.layouts.*
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope

/**
 * Gestionnaire des layouts gamepad par console avec support des variantes
 * Supporte maintenant les overlays RetroArch en plus de Lemuroid
 */
object GamePadLayoutManager {
    
    private const val TAG = "GamePadLayoutManager"
    
    enum class LayoutVariant {
        DEFAULT,
        DUALSHOCK,  // PSX with analog sticks
        BASIC,       // PSX without analog sticks
        THREE_BUTTON,  // Genesis 3-button
        SIX_BUTTON,     // Genesis 6-button
        RETROARCH    // RetroArch overlay system
    }
    
    data class LayoutPair(
        val left: @Composable PadKitScope.(Modifier, TouchControllerSettingsManager.Settings) -> Unit,
        val right: @Composable PadKitScope.(Modifier, TouchControllerSettingsManager.Settings) -> Unit
    )
    
    /**
     * Obtenir les variantes disponibles pour une console
     */
    fun getAvailableVariants(console: String): List<Pair<LayoutVariant, String>> {
        return when (console.lowercase()) {
            "psx", "ps1", "playstation" -> listOf(
                LayoutVariant.DUALSHOCK to "DualShock (Analog Sticks)",
                LayoutVariant.BASIC to "Basic (No Analog)",
                LayoutVariant.RETROARCH to "RetroArch Overlay"
            )
            "genesis", "megadrive", "md" -> listOf(
                LayoutVariant.SIX_BUTTON to "6-Button",
                LayoutVariant.THREE_BUTTON to "3-Button",
                LayoutVariant.RETROARCH to "RetroArch Overlay"
            )
            else -> listOf(
                LayoutVariant.DEFAULT to "Default",
                LayoutVariant.RETROARCH to "RetroArch Overlay"
            )
        }
    }
    
    /**
     * Charger la variante sauvegardée pour une console
     */
    fun loadVariant(prefs: SharedPreferences, console: String): LayoutVariant {
        val key = "gamepad_${console}_variant"
        val variantName = prefs.getString(key, "DEFAULT") ?: "DEFAULT"
        return try {
            LayoutVariant.valueOf(variantName)
        } catch (e: Exception) {
            LayoutVariant.DEFAULT
        }
    }
    
    /**
     * Sauvegarder la variante choisie
     */
    fun saveVariant(prefs: SharedPreferences, console: String, variant: LayoutVariant) {
        // Utiliser commit() pour synchronisation immédiate et déclenchement du listener
        prefs.edit().putString("gamepad_${console}_variant", variant.name).commit()
    }
    
    /**
     * Obtenir le layout approprié selon console et variante
     * Note: Pour RETROARCH, context et prefs doivent être passés via getLayoutWithContext()
     */
    fun getLayout(console: String, variant: LayoutVariant): LayoutPair {
        // Pour RetroArch, retourner un placeholder - utiliser getLayoutWithContext() à la place
        if (variant == LayoutVariant.RETROARCH) {
            Log.w(TAG, "RETROARCH variant requires context - use getLayoutWithContext() instead")
            // Retourner layout par défaut en fallback
            return getLayout(console, LayoutVariant.DEFAULT)
        }
        
        return when (console.lowercase()) {
            "psx", "ps1", "playstation" -> when (variant) {
                LayoutVariant.DUALSHOCK -> LayoutPair(
                    left = { mod, set -> PSXDualShockLeft(mod, set) },
                    right = { mod, set -> PSXDualShockRight(mod, set) }
                )
                LayoutVariant.BASIC -> LayoutPair(
                    left = { mod, set -> PSXLeft(mod, set) },
                    right = { mod, set -> PSXRight(mod, set) }
                )
                else -> LayoutPair(
                    left = { mod, set -> PSXDualShockLeft(mod, set) },
                    right = { mod, set -> PSXDualShockRight(mod, set) }
                )
            }
            
            "genesis", "megadrive", "md" -> when (variant) {
                LayoutVariant.SIX_BUTTON -> LayoutPair(
                    left = { mod, set -> Genesis6Left(mod, set) },
                    right = { mod, set -> Genesis6Right(mod, set) }
                )
                LayoutVariant.THREE_BUTTON -> LayoutPair(
                    left = { mod, set -> Genesis3Left(mod, set) },
                    right = { mod, set -> Genesis3Right(mod, set) }
                )
                else -> LayoutPair(
                    left = { mod, set -> Genesis6Left(mod, set) },
                    right = { mod, set -> Genesis6Right(mod, set) }
                )
            }
            
            "n64" -> LayoutPair(
                left = { mod, set -> N64Left(mod, set) },
                right = { mod, set -> N64Right(mod, set) }
            )
            
            "snes" -> LayoutPair(
                left = { mod, set -> SNESLeft(mod, set) },
                right = { mod, set -> SNESRight(mod, set) }
            )
            
            "nes" -> LayoutPair(
                left = { mod, set -> NESLeft(mod, set) },
                right = { mod, set -> NESRight(mod, set) }
            )
            
            "gba" -> LayoutPair(
                left = { mod, set -> GBALeft(mod, set) },
                right = { mod, set -> GBARight(mod, set) }
            )
            
            "gb", "gbc" -> LayoutPair(
                left = { mod, set -> GBLeft(mod, set) },
                right = { mod, set -> GBRight(mod, set) }
            )
            
            "psp" -> LayoutPair(
                left = { mod, set -> PSPLeft(mod, set) },
                right = { mod, set -> PSPRight(mod, set) }
            )
            
            else -> LayoutPair(
                left = { mod, set -> NESLeft(mod, set) },
                right = { mod, set -> NESRight(mod, set) }
            )
        }
    }
    
    /**
     * Obtenir le layout avec support complet pour RetroArch (nécessite Context)
     * Utiliser cette fonction au lieu de getLayout() pour RETROARCH variant
     */
    fun getLayoutWithContext(
        console: String,
        variant: LayoutVariant,
        context: Context,
        prefs: SharedPreferences,
        onButtonPress: (List<Int>) -> Unit,
        onButtonRelease: (List<Int>) -> Unit,
        onLayoutSwitch: (String) -> Unit,
        onMenuToggle: () -> Unit,
        onHotkeyChange: (String, Boolean) -> Unit = { _, _ -> }
    ): LayoutPair {
        if (variant == LayoutVariant.RETROARCH) {
            return createRetroArchLayout(console, context, prefs, onButtonPress, onButtonRelease, onLayoutSwitch, onMenuToggle, onHotkeyChange)
        }
        
        // Pour les autres variantes, utiliser la méthode normale
        return getLayout(console, variant)
    }
    
    /**
     * Créer un LayoutPair pour RetroArch overlay
     */
    private fun createRetroArchLayout(
        console: String,
        context: Context,
        prefs: SharedPreferences,
        onButtonPress: (List<Int>) -> Unit,
        onButtonRelease: (List<Int>) -> Unit,
        onLayoutSwitch: (String) -> Unit,
        onMenuToggle: () -> Unit,
        onHotkeyChange: (String, Boolean) -> Unit
    ): LayoutPair {
        return LayoutPair(
            left = { mod, set -> 
                // Côté gauche: vide (l'overlay est fullscreen)
                Box(modifier = mod) {}
            },
            right = { mod, set ->
                // Côté droit: Overlay RetroArch fullscreen
                RetroArchOverlayComposable(
                    console = console,
                    context = context,
                    prefs = prefs,
                    modifier = mod,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease,
                    onLayoutSwitch = onLayoutSwitch,
                    onMenuToggle = onMenuToggle,
                    onHotkeyChange = onHotkeyChange
                )
            }
        )
    }
    
    /**
     * Composable wrapper pour RetroArch overlay
     * Gère le chargement de la config et la détection d'orientation
     */
    @Composable
    private fun PadKitScope.RetroArchOverlayComposable(
        console: String,
        context: Context,
        prefs: SharedPreferences,
        modifier: Modifier,
        onButtonPress: (List<Int>) -> Unit,
        onButtonRelease: (List<Int>) -> Unit,
        onLayoutSwitch: (String) -> Unit,
        onMenuToggle: () -> Unit,
        onHotkeyChange: (String, Boolean) -> Unit
    ) {
        // Charger les préférences d'overlay
        val overlayPreference = remember(console) {
            OverlayPreferenceManager.load(prefs, console)
        }
        
        if (overlayPreference == null) {
            // Pas d'overlay configuré - afficher message ou fallback
            Box(modifier = modifier) {
                // TODO: Afficher un message pour configurer l'overlay
                Log.w(TAG, "No RetroArch overlay configured for $console")
            }
            return
        }
        
        // Déterminer l'orientation actuelle
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // Choisir le layout approprié selon orientation
        val layoutName = if (overlayPreference.autoRotate) {
            if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
        } else {
            overlayPreference.landscapeLayout
        }
        
        // Charger la configuration de l'overlay
        val assetManager = remember { OverlayAssetManager(context) }
        val overlayConfig = remember(overlayPreference.overlayName, overlayPreference.customCfgName, console) {
            assetManager.loadOverlayConfig(overlayPreference.overlayName, console, overlayPreference.customCfgName)
        }
        
        if (overlayConfig == null) {
            Box(modifier = modifier) {
                Log.e(TAG, "Failed to load overlay config: ${overlayPreference.overlayName}")
            }
            return
        }
        
        // Obtenir le layout spécifique
        val layout = overlayConfig.layouts[layoutName]
        if (layout == null) {
            Box(modifier = modifier) {
                Log.e(TAG, "Layout not found: $layoutName in ${overlayPreference.overlayName}")
            }
            return
        }
        
        // Afficher l'overlay
        RetroArchOverlayScreen(
            layout = layout,
            overlayName = overlayPreference.overlayName,
            assetManager = assetManager,
            onButtonPress = { action ->
                // Convertir action RetroArch → KeyCodes Android
                val keyCodes = RetroArchButtonMapping.parseAction(action)
                if (keyCodes.isNotEmpty()) {
                    onButtonPress(keyCodes)
                }
            },
            onButtonRelease = { action ->
                val keyCodes = RetroArchButtonMapping.parseAction(action)
                if (keyCodes.isNotEmpty()) {
                    onButtonRelease(keyCodes)
                }
            },
            onLayoutSwitch = onLayoutSwitch,
            onMenuToggle = onMenuToggle,
            onHotkeyChange = onHotkeyChange,
            swapAnalogSticks = overlayPreference.swapAnalogSticks,
            invertAnalogLeftY = overlayPreference.invertAnalogLeftY,
            invertAnalogRightY = overlayPreference.invertAnalogRightY,
            overlayScale = overlayPreference.scale,
            overlayXOffset = overlayPreference.xOffset,
            overlayYOffset = overlayPreference.yOffset,
            overlayXSeparation = overlayPreference.xSeparation,
            overlayYSeparation = overlayPreference.ySeparation,
            modifier = modifier
        )
    }
}

