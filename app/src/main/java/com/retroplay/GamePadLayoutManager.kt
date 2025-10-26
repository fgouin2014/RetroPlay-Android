package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.layouts.*
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope

/**
 * Gestionnaire des layouts gamepad par console avec support des variantes
 */
object GamePadLayoutManager {
    
    enum class LayoutVariant {
        DEFAULT,
        DUALSHOCK,  // PSX with analog sticks
        BASIC,       // PSX without analog sticks
        THREE_BUTTON,  // Genesis 3-button
        SIX_BUTTON     // Genesis 6-button
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
                LayoutVariant.BASIC to "Basic (No Analog)"
            )
            "genesis", "megadrive", "md" -> listOf(
                LayoutVariant.SIX_BUTTON to "6-Button",
                LayoutVariant.THREE_BUTTON to "3-Button"
            )
            else -> listOf(LayoutVariant.DEFAULT to "Default")
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
        prefs.edit().putString("gamepad_${console}_variant", variant.name).apply()
    }
    
    /**
     * Obtenir le layout approprié selon console et variante
     */
    fun getLayout(console: String, variant: LayoutVariant): LayoutPair {
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
}

