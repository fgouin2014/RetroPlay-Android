package com.retroplay

/**
 * Représente une variable de core LibretroDroid.
 * Peut être soit un DIP Switch (hardware arcade) soit une Core Option (paramètre émulateur).
 */
data class CoreVariable(
    val key: String,                    // Ex: "mame2010-dip-Difficulty" ou "mame2010-frameskip"
    val displayName: String,            // Ex: "Difficulty" ou "Frameskip"
    val currentValue: String,           // Ex: "Normal" ou "0"
    val possibleValues: List<String>,   // Ex: ["Easy", "Normal", "Hard"]
    val isDipSwitch: Boolean            // true = DIP switch, false = Core Option
) {
    /**
     * Vérifie si c'est une variable booléenne (enabled/disabled ou on/off)
     */
    fun isBoolean(): Boolean {
        val values = possibleValues.map { it.lowercase() }.toSet()
        return values == setOf("enabled", "disabled") || 
               values == setOf("on", "off") ||
               values == setOf("true", "false")
    }
    
    /**
     * Retourne la valeur booléenne actuelle (si applicable)
     */
    fun getBooleanValue(): Boolean {
        return when (currentValue.lowercase()) {
            "enabled", "on", "true" -> true
            else -> false
        }
    }
}


