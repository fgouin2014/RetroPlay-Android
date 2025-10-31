package com.retroplay.shader

import com.swordfish.libretrodroid.ShaderConfig

/**
 * Shader Manager - Gestion des shaders LibretroDroid
 * 
 * 7 shaders disponibles :
 * - Default (aucun effet)
 * - CRT (scanlines, effet TV cathodique)
 * - LCD (grille LCD, effet handheld)
 * - Sharp (pixels nets, pas d'anti-aliasing)
 * - CUT (upscale intelligent)
 * - CUT2 (upscale avancé)
 * - CUT3 (upscale maximum qualité)
 */
object ShaderManager {
    
    enum class ShaderPreset(val displayName: String, val icon: String) {
        DEFAULT("None (Fast)", "◻️"),
        CRT("CRT (Scanlines)", "📺"),
        LCD("LCD (Handheld)", "🎮"),
        SHARP("Sharp (Pixels)", "🔲"),
        CUT("Upscale (Low)", "⬆️"),
        CUT2("Upscale (Med)", "⬆️"),
        CUT3("Upscale (High)", "⬆️")
    }
    
    /**
     * Convertir preset vers ShaderConfig LibretroDroid
     */
    fun getShaderConfig(preset: ShaderPreset): ShaderConfig {
        return when (preset) {
            ShaderPreset.DEFAULT -> ShaderConfig.Default
            ShaderPreset.CRT -> ShaderConfig.CRT
            ShaderPreset.LCD -> ShaderConfig.LCD
            ShaderPreset.SHARP -> ShaderConfig.Sharp
            ShaderPreset.CUT -> ShaderConfig.CUT()  // Defaults
            ShaderPreset.CUT2 -> ShaderConfig.CUT2()  // Defaults
            ShaderPreset.CUT3 -> ShaderConfig.CUT3()  // Defaults
        }
    }
    
    /**
     * Obtenir le prochain shader dans le cycle
     */
    fun getNextShader(current: ShaderPreset): ShaderPreset {
        val values = ShaderPreset.values()
        val currentIndex = values.indexOf(current)
        val nextIndex = (currentIndex + 1) % values.size
        return values[nextIndex]
    }
    
    /**
     * Obtenir le shader précédent dans le cycle
     */
    fun getPreviousShader(current: ShaderPreset): ShaderPreset {
        val values = ShaderPreset.values()
        val currentIndex = values.indexOf(current)
        val prevIndex = if (currentIndex == 0) values.size - 1 else currentIndex - 1
        return values[prevIndex]
    }
    
    /**
     * Convertir String (sauvegardé) vers preset
     */
    fun fromString(name: String): ShaderPreset {
        return try {
            ShaderPreset.valueOf(name)
        } catch (e: Exception) {
            ShaderPreset.DEFAULT
        }
    }
}

