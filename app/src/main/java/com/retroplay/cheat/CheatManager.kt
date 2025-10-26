package com.retroplay.cheat

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Gestionnaire de codes de triche
 * Support:
 * - Fichiers .cht de RetroArch (depuis assets ou custom)
 * - Codes personnalisés (GameShark, Game Genie, Action Replay)
 */
class CheatManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CheatManager"
        
        // Chemins de stockage (NOUVELLE STRUCTURE)
        const val CHEAT_DIR = "/storage/emulated/0/GameLibrary-Data/cheats"
        const val RETROARCH_CHEATS_DIR = "$CHEAT_DIR/retroarch"
        const val RETROARCH_OVERRIDE_DIR = "$RETROARCH_CHEATS_DIR/overrides"  // États ON/OFF RetroArch
        const val USER_CHEATS_DIR = "$CHEAT_DIR/user"  // Codes utilisateur personnalisés
    }
    
    /**
     * Structure d'un code de triche
     */
    data class Cheat(
        val description: String,
        val code: String,
        val enabled: Boolean = false,
        val type: CheatType = CheatType.RETROARCH
    )
    
    enum class CheatType {
        RETROARCH,      // Format RetroArch .cht
        GAMESHARK,      // GameShark (PSX, N64)
        GAME_GENIE,     // Game Genie (NES, SNES, Genesis)
        ACTION_REPLAY,  // Action Replay (diverses consoles)
        CUSTOM          // Format personnalisé
    }
    
    /**
     * Charger les codes depuis un fichier .cht RetroArch
     * 
     * Format RetroArch:
     * cheats = 3
     * cheat0_desc = "Infinite Health"
     * cheat0_code = "8009C6E4+03E7"
     * cheat0_enable = false
     */
    fun loadRetroArchCheats(chtFile: File): List<Cheat> {
        if (!chtFile.exists() || !chtFile.name.endsWith(".cht")) {
            Log.w(TAG, "Invalid .cht file: ${chtFile.absolutePath}")
            return emptyList()
        }
        
        // Déterminer le type selon le chemin du fichier
        val cheatType = if (chtFile.absolutePath.contains("/user/")) {
            CheatType.CUSTOM
        } else {
            CheatType.RETROARCH
        }
        
        val cheats = mutableListOf<Cheat>()
        
        try {
            val lines = chtFile.readLines()
            val cheatMap = mutableMapOf<Int, MutableMap<String, String>>()
            
            // Parser le fichier ligne par ligne
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                
                // Extraire cheatX_property = "value" ou cheatX_property = value
                // Regex améliorée pour capturer entre guillemets OU sans guillemets
                val match = Regex("""cheat(\d+)_(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""").find(trimmed)
                if (match != null) {
                    val index = match.groupValues[1].toInt()
                    val property = match.groupValues[2]
                    // Prendre la première valeur non-vide (guillemets doubles, simples, ou sans)
                    val value = match.groupValues[3].ifEmpty { 
                        match.groupValues[4].ifEmpty { 
                            match.groupValues[5] 
                        }
                    }
                    
                    cheatMap.getOrPut(index) { mutableMapOf() }[property] = value
                    Log.d(TAG, "Parsed: cheat$index.$property = '$value'")
                }
            }
            
            // Convertir en objets Cheat
            cheatMap.forEach { (_, properties) ->
                val desc = properties["desc"]?.trim() ?: "Unknown Cheat"
                val code = properties["code"]?.trim() ?: ""
                val enabled = properties["enable"]?.trim()?.toBoolean() ?: false
                
                if (code.isNotEmpty()) {
                    cheats.add(Cheat(desc, code, enabled, cheatType))  // Utiliser le type détecté
                }
            }
            
            Log.i(TAG, "Loaded ${cheats.size} cheats from ${chtFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading RetroArch cheats", e)
        }
        
        return cheats
    }
    
    /**
     * Trouver le fichier .cht pour un jeu donné
     * 
     * Recherche dans:
     * 1. Custom cheats: /GameLibrary-Data/cheats/custom/{console}/{gameName}.cht
     * 2. RetroArch cheats: /GameLibrary-Data/cheats/retroarch/{console}/{gameName}.cht
     */
    fun findCheatFile(console: String, gameName: String, romPath: String? = null): File? {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        
        Log.d(TAG, "Searching cheat file for: '$gameName' → sanitized: '$sanitizedName'")
        
        // Essayer plusieurs variantes du nom
        val variants = listOf(
            gameName,                    // Nom exact
            sanitizedName,               // Nom nettoyé
            gameName.trim(),             // Sans espaces début/fin
            sanitizedName.replace(" ", "")  // Sans espaces du tout
        )
        
        for (name in variants) {
            // Chercher dans user d'abord
            val userFile = File("$USER_CHEATS_DIR/$console/$name.cht")
            if (userFile.exists()) {
                Log.i(TAG, "✅ Found user cheat file: ${userFile.absolutePath}")
                return userFile
            }
            
            // Puis dans RetroArch (avec sous-répertoires)
            val retroarchFile = File("$RETROARCH_CHEATS_DIR/$console/$name.cht")
            if (retroarchFile.exists()) {
                Log.i(TAG, "✅ Found RetroArch cheat file: ${retroarchFile.absolutePath}")
                return retroarchFile
            }
        }
        
        // RECHERCHE INTELLIGENTE : Par région et cheat device
        Log.d(TAG, "romPath = $romPath, will try intelligent matching...")
        
        val matcher = CheatMatcher()
        val retroarchDir = File("$RETROARCH_CHEATS_DIR/$console")
        val matched = matcher.findCompatibleCheatFile(console, gameName, romPath ?: "", retroarchDir)
        if (matched != null) {
            return matched
        }
        
        Log.w(TAG, "❌ No cheat file found for '$gameName' ($console)")
        Log.w(TAG, "Tried variants: ${variants.joinToString(", ")}")
        return null
    }
    
    /**
     * Chercher un fichier cheat SEULEMENT dans RetroArch (pas user/)
     */
    private fun findRetroArchCheatFile(console: String, gameName: String, romPath: String? = null): File? {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        
        // Essayer plusieurs variantes du nom
        val variants = listOf(
            gameName,
            sanitizedName,
            gameName.trim(),
            sanitizedName.replace(" ", "")
        )
        
        for (name in variants) {
            val retroarchFile = File("$RETROARCH_CHEATS_DIR/$console/$name.cht")
            if (retroarchFile.exists()) {
                return retroarchFile
            }
        }
        
        // Recherche intelligente dans les sous-répertoires RetroArch
        val matcher = CheatMatcher()
        val retroarchDir = File("$RETROARCH_CHEATS_DIR/$console")
        return matcher.findCompatibleCheatFile(console, gameName, romPath ?: "", retroarchDir)
    }
    
    /**
     * Charger tous les codes pour un jeu
     * Combine RetroArch + User + états activés depuis .override
     */
    fun loadCheatsForGame(console: String, gameName: String, romPath: String? = null): List<Cheat> {
        val allCheats = mutableListOf<Cheat>()
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        
        // 1. Charger EXPLICITEMENT les codes RetroArch (recherche SEULEMENT dans retroarch/)
        val retroarchFile = findRetroArchCheatFile(console, gameName, romPath)
        if (retroarchFile != null) {
            allCheats.addAll(loadRetroArchCheats(retroarchFile))
            Log.i(TAG, "[RetroArch] Loaded ${allCheats.size} cheats from: ${retroarchFile.name}")
        }
        
        // 2. Charger EXPLICITEMENT les codes User (recherche SEULEMENT dans user/)
        val userFile = File("$USER_CHEATS_DIR/$console/$sanitizedName.cht")
        if (userFile.exists()) {
            val userCheatsCount = allCheats.size
            allCheats.addAll(loadRetroArchCheats(userFile))
            Log.i(TAG, "[User] Loaded ${allCheats.size - userCheatsCount} cheats from: ${userFile.name}")
        }
        
        // 3. Charger les états override (quels codes sont activés)
        val enabledStates = loadEnabledStates(console, gameName)
        
        // 4. Combiner : codes du .cht + états activés de .override
        return allCheats.map { cheat ->
            val key = "${cheat.description}::${cheat.code}"
            cheat.copy(enabled = enabledStates[key] ?: cheat.enabled)
        }
    }
    
    /**
     * Charger les états activés depuis le fichier .override
     */
    private fun loadEnabledStates(console: String, gameName: String): Map<String, Boolean> {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        val overrideFile = File("$RETROARCH_OVERRIDE_DIR/$console/$sanitizedName.override")
        
        if (!overrideFile.exists()) {
            return emptyMap()
        }
        
        val states = mutableMapOf<String, Boolean>()
        
        try {
            overrideFile.readLines().forEach { line ->
                val parts = line.split("::", limit = 3)
                if (parts.size == 3) {
                    val desc = parts[0]
                    val code = parts[1]
                    val enabled = parts[2].toBoolean()
                    val key = "$desc::$code"
                    states[key] = enabled
                }
            }
            
            Log.d(TAG, "Loaded ${states.size} override states from ${overrideFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading override states", e)
        }
        
        return states
    }
    
    /**
     * Sauvegarder UNIQUEMENT les états activés (ne touche PAS aux fichiers .cht)
     */
    fun saveEnabledCheats(console: String, gameName: String, cheats: List<Cheat>) {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        val overrideDir = File("$RETROARCH_OVERRIDE_DIR/$console")
        
        if (!overrideDir.exists()) {
            overrideDir.mkdirs()
        }
        
        val overrideFile = File(overrideDir, "$sanitizedName.override")
        
        try {
            overrideFile.bufferedWriter().use { writer ->
                cheats.forEach { cheat ->
                    // Format: description::code::enabled
                    writer.write("${cheat.description}::${cheat.code}::${cheat.enabled}\n")
                }
            }
            
            Log.i(TAG, "💾 Saved ${cheats.size} cheat states to ${overrideFile.absolutePath} (READ-ONLY .cht preserved)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cheat states", e)
        }
    }
    
    /**
     * Créer un code personnalisé et l'ajouter au fichier .cht custom
     */
    fun createCustomCheat(
        console: String,
        gameName: String,
        description: String,
        code: String,
        type: CheatType = CheatType.CUSTOM
    ): Cheat {
        return Cheat(description, code, enabled = false, type = type)
    }
    
    /**
     * Ajouter un code personnalisé au fichier .cht custom
     * (SEULEMENT pour les codes ajoutés manuellement, PAS pour RetroArch)
     */
    fun addCustomCheatToFile(console: String, gameName: String, cheat: Cheat) {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        val userDir = File("$USER_CHEATS_DIR/$console")
        
        if (!userDir.exists()) {
            userDir.mkdirs()
        }
        
        val userFile = File(userDir, "$sanitizedName.cht")
        val existingCheats = if (userFile.exists()) {
            loadRetroArchCheats(userFile).toMutableList()
        } else {
            mutableListOf()
        }
        
        // Ajouter le nouveau code
        existingCheats.add(cheat)
        
        // Réécrire le fichier
        try {
            userFile.bufferedWriter().use { writer ->
                writer.write("cheats = ${existingCheats.size}\n\n")
                
                existingCheats.forEachIndexed { index, c ->
                    writer.write("cheat${index}_desc = \"${c.description}\"\n")
                    writer.write("cheat${index}_code = \"${c.code}\"\n")
                    writer.write("cheat${index}_enable = false\n\n")  // Toujours false au départ
                }
            }
            
            Log.i(TAG, "✅ Added user cheat to ${userFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding custom cheat", e)
        }
    }
    
    /**
     * Supprimer un code personnalisé du fichier .cht custom
     */
    fun deleteCustomCheat(console: String, gameName: String, cheat: Cheat) {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9 -]"), "").trim()
        val userFile = File("$USER_CHEATS_DIR/$console/$sanitizedName.cht")
        
        if (!userFile.exists()) {
            Log.w(TAG, "User file doesn't exist: ${userFile.absolutePath}")
            return
        }
        
        try {
            val existingCheats = loadRetroArchCheats(userFile).toMutableList()
            val before = existingCheats.size
            
            // Retirer le code
            existingCheats.removeAll { it.description == cheat.description && it.code == cheat.code }
            
            val after = existingCheats.size
            
            if (before == after) {
                Log.w(TAG, "Cheat not found in file: ${cheat.description}")
                return
            }
            
            // Réécrire le fichier (ou supprimer si vide)
            if (existingCheats.isEmpty()) {
                userFile.delete()
                Log.i(TAG, "🗑️ Deleted empty user file: ${userFile.name}")
            } else {
                userFile.bufferedWriter().use { writer ->
                    writer.write("cheats = ${existingCheats.size}\n\n")
                    
                    existingCheats.forEachIndexed { index, c ->
                        writer.write("cheat${index}_desc = \"${c.description}\"\n")
                        writer.write("cheat${index}_code = \"${c.code}\"\n")
                        writer.write("cheat${index}_enable = false\n\n")
                    }
                }
                
                Log.i(TAG, "🗑️ Deleted user cheat from ${userFile.name} ($before → $after codes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting custom cheat", e)
        }
    }
    
    /**
     * Valider un code de triche selon son type
     */
    fun validateCheatCode(code: String, type: CheatType): Boolean {
        return when (type) {
            CheatType.RETROARCH -> {
                // Format: 8009C6E4+03E7 ou 300A1234+00FF
                code.matches(Regex("""[0-9A-F]{8}\+[0-9A-F]{4}""", RegexOption.IGNORE_CASE))
            }
            CheatType.GAMESHARK -> {
                // Format: 8009C6E4 03E7 (PSX) ou 81234567 00FF (N64)
                code.matches(Regex("""[0-9A-F]{8}\s+[0-9A-F]{4,8}""", RegexOption.IGNORE_CASE))
            }
            CheatType.GAME_GENIE -> {
                // Format variable selon console (6-8 caractères alphanumériques)
                code.matches(Regex("""[A-Z0-9]{6,8}""", RegexOption.IGNORE_CASE))
            }
            CheatType.ACTION_REPLAY -> {
                // Format: 12345678 ABCDEF01
                code.matches(Regex("""[0-9A-F]{8}\s+[0-9A-F]{8}""", RegexOption.IGNORE_CASE))
            }
            CheatType.CUSTOM -> true // Accepte n'importe quoi
        }
    }
}

