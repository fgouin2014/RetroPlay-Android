package com.retroplay.config

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import java.io.File

/**
 * Tests unitaires pour RetroPlayConfigManager
 * Vérifie que les fichiers .cfg sont créés, lus et sauvegardés correctement
 * pour les cores et les jeux.
 * 
 * NOTE: Ces tests nécessitent un accès au système de fichiers Android.
 * Pour des tests complets, utilisez des tests instrumentés (androidTest).
 */
class RetroPlayConfigManagerTest {

/**
 * Tests unitaires pour RetroPlayConfigManager
 * Vérifie que les fichiers .cfg sont créés, lus et sauvegardés correctement
 * pour les cores et les jeux.
 */
class RetroPlayConfigManagerTest {
    
    private val testConsole = "nes"
    private val testGameName = "Duck Hunt"
    private val testCorePath = "fceumm_libretro_android.so"
    
    @Before
    fun setUp() {
        // Nettoyer les fichiers de test avant chaque test
        cleanupTestFiles()
    }
    
    @After
    fun tearDown() {
        // Nettoyer les fichiers de test après chaque test
        cleanupTestFiles()
    }
    
    private fun cleanupTestFiles() {
        val configDir = File("/storage/emulated/0/GameLibrary-Data/config/$testConsole")
        if (configDir.exists()) {
            configDir.listFiles()?.forEach { file ->
                if (file.name.contains("fceumm") || file.name.contains("Duck_Hunt") || file.name.contains("retroplay")) {
                    file.delete()
                }
            }
        }
    }
    
    @Test
    fun testLoadConfig_CreatesDefaultConfigFile() {
        // Test: Charger une config inexistante doit créer un fichier par défaut
        val config = RetroPlayConfigManager.loadConfig(testConsole, testCorePath)
        
        // Vérifier que le fichier existe avec le nom du core
        val expectedPath = "/storage/emulated/0/GameLibrary-Data/config/$testConsole/fceumm.cfg"
        val configFile = File(expectedPath)
        
        assertTrue("Le fichier de config doit être créé: $expectedPath", configFile.exists())
        assertNotNull("La config doit être retournée", config)
    }
    
    @Test
    fun testLoadConfig_UsesCoreName() {
        // Test: Le fichier de config doit utiliser le nom du core, pas "retroplay.cfg"
        RetroPlayConfigManager.loadConfig(testConsole, testCorePath)
        
        val coreConfigFile = File("/storage/emulated/0/GameLibrary-Data/config/$testConsole/fceumm.cfg")
        val retroplayConfigFile = File("/storage/emulated/0/GameLibrary-Data/config/$testConsole/retroplay.cfg")
        
        assertTrue("Le fichier fceumm.cfg doit exister", coreConfigFile.exists())
        // Le fichier retroplay.cfg ne doit PAS être créé si on passe le core path
    }
    
    @Test
    fun testSaveConfig_CreatesFileWithCoreName() {
        // Test: Sauvegarder une config doit créer le fichier avec le nom du core
        val config = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = true,
            runAheadFrames = 2
        )
        
        RetroPlayConfigManager.saveConfig(testConsole, config, testCorePath)
        
        val expectedPath = "/storage/emulated/0/GameLibrary-Data/config/$testConsole/fceumm.cfg"
        val configFile = File(expectedPath)
        
        assertTrue("Le fichier doit être créé", configFile.exists())
        
        // Vérifier le contenu
        val content = configFile.readText()
        assertTrue("Le fichier doit contenir run_ahead_enabled", content.contains("run_ahead_enabled"))
        assertTrue("Le fichier doit contenir la valeur true", content.contains("true"))
    }
    
    @Test
    fun testLoadConfig_ReadsSavedConfig() {
        // Test: Charger une config sauvegardée doit retourner les bonnes valeurs
        val originalConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = true,
            runAheadFrames = 3,
            videoVsync = false
        )
        
        RetroPlayConfigManager.saveConfig(testConsole, originalConfig, testCorePath)
        
        // Recharger
        val loadedConfig = RetroPlayConfigManager.loadConfig(testConsole, testCorePath)
        
        assertEquals("runAheadEnabled doit être préservé", originalConfig.runAheadEnabled, loadedConfig.runAheadEnabled)
        assertEquals("runAheadFrames doit être préservé", originalConfig.runAheadFrames, loadedConfig.runAheadFrames)
        assertEquals("videoVsync doit être préservé", originalConfig.videoVsync, loadedConfig.videoVsync)
    }
    
    @Test
    fun testSaveGameConfig_CreatesPerGameFile() {
        // Test: Sauvegarder une config per-game doit créer le fichier {gameName}.cfg
        val config = RetroPlayConfigManager.RetroPlayConfig(
            zapperEnabled = true,
            zapperPort = 1
        )
        
        RetroPlayConfigManager.saveGameConfig(testConsole, testGameName, config, testCorePath)
        
        // Le nom du jeu doit être sanitized (espaces remplacés par _)
        val expectedPath = "/storage/emulated/0/GameLibrary-Data/config/$testConsole/Duck_Hunt.cfg"
        val gameConfigFile = File(expectedPath)
        
        assertTrue("Le fichier per-game doit être créé: $expectedPath", gameConfigFile.exists())
    }
    
    @Test
    fun testSaveGameConfig_CommentsOutMatchingValues() {
        // Test: Les valeurs identiques à la config globale doivent être commentées
        val globalConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = false,
            runAheadFrames = 1,
            zapperEnabled = true  // Différent
        )
        
        val gameConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = false,  // Identique -> doit être commenté
            runAheadFrames = 1,       // Identique -> doit être commenté
            zapperEnabled = false      // Différent -> doit être actif
        )
        
        // Sauvegarder la config globale d'abord
        RetroPlayConfigManager.saveConfig(testConsole, globalConfig, testCorePath)
        
        // Sauvegarder la config per-game
        RetroPlayConfigManager.saveGameConfig(testConsole, testGameName, gameConfig, testCorePath)
        
        val gameConfigFile = File("/storage/emulated/0/GameLibrary-Data/config/$testConsole/Duck_Hunt.cfg")
        assertTrue("Le fichier per-game doit exister", gameConfigFile.exists())
        
        val content = gameConfigFile.readText()
        
        // Vérifier que les valeurs identiques sont commentées
        assertTrue("run_ahead_enabled identique doit être commenté", 
            content.contains("# run_ahead_enabled") || content.contains("# run_ahead_enabled = \"false\""))
        
        // Vérifier que les valeurs différentes ne sont PAS commentées
        assertTrue("zapper_enabled différent ne doit PAS être commenté", 
            content.contains("zapper_enabled = \"false\"") && !content.contains("# zapper_enabled = \"false\""))
    }
    
    @Test
    fun testGetEffectiveConfig_MergesGlobalAndPerGame() {
        // Test: getEffectiveConfig doit fusionner config globale + per-game
        val globalConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = false,
            runAheadFrames = 1,
            zapperEnabled = false
        )
        
        val gameConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = false,  // Identique -> utilise global
            runAheadFrames = 2,      // Différent -> utilise per-game
            zapperEnabled = true      // Différent -> utilise per-game
        )
        
        // Sauvegarder les deux
        RetroPlayConfigManager.saveConfig(testConsole, globalConfig, testCorePath)
        RetroPlayConfigManager.saveGameConfig(testConsole, testGameName, gameConfig, testCorePath)
        
        // Obtenir la config effective
        val effectiveConfig = RetroPlayConfigManager.getEffectiveConfig(testConsole, testGameName, testCorePath)
        
        // Vérifier que les valeurs per-game sont utilisées
        assertEquals("runAheadFrames doit venir de per-game", 2, effectiveConfig.runAheadFrames)
        assertEquals("zapperEnabled doit venir de per-game", true, effectiveConfig.zapperEnabled)
        assertEquals("runAheadEnabled doit venir de global (identique)", false, effectiveConfig.runAheadEnabled)
    }
    
    @Test
    fun testExtractCoreName_FromDifferentPaths() {
        // Test: Extraction du nom du core depuis différents chemins
        val testCases = mapOf(
            "fceumm_libretro_android.so" to "fceumm",
            "/path/to/snes9x_libretro_android.so" to "snes9x",
            "pcsx_rearmed_libretro_android.so" to "pcsx_rearmed",
            "parallel_n64_libretro_android.so" to "parallel_n64"
        )
        
        testCases.forEach { (corePath, expectedCoreName) ->
            val config = RetroPlayConfigManager.RetroPlayConfig()
            RetroPlayConfigManager.saveConfig("test", config, corePath)
            
            // Le fichier doit être créé avec le nom du core
            val expectedFile = File("/storage/emulated/0/GameLibrary-Data/config/test/$expectedCoreName.cfg")
            assertTrue("Le fichier doit être créé avec le nom $expectedCoreName pour $corePath", 
                expectedFile.exists())
            
            // Nettoyer
            expectedFile.delete()
        }
    }
    
    @Test
    fun testHasGameConfig_ReturnsTrueWhenFileExists() {
        // Test: hasGameConfig doit retourner true si le fichier existe
        val config = RetroPlayConfigManager.RetroPlayConfig()
        RetroPlayConfigManager.saveGameConfig(testConsole, testGameName, config, testCorePath)
        
        val hasConfig = RetroPlayConfigManager.hasGameConfig(testConsole, testGameName)
        assertTrue("hasGameConfig doit retourner true si le fichier existe", hasConfig)
    }
    
    @Test
    fun testHasGameConfig_ReturnsFalseWhenFileDoesNotExist() {
        // Test: hasGameConfig doit retourner false si le fichier n'existe pas
        val hasConfig = RetroPlayConfigManager.hasGameConfig(testConsole, "NonExistentGame")
        assertFalse("hasGameConfig doit retourner false si le fichier n'existe pas", hasConfig)
    }
    
    @Test
    fun testFallbackToRetroplayCfg_WhenCorePathIsNull() {
        // Test: Si coreFilePath est null, doit utiliser retroplay.cfg comme fallback
        val config = RetroPlayConfigManager.RetroPlayConfig()
        RetroPlayConfigManager.saveConfig(testConsole, config, null)
        
        val retroplayFile = File("/storage/emulated/0/GameLibrary-Data/config/$testConsole/retroplay.cfg")
        assertTrue("Le fichier retroplay.cfg doit être créé quand corePath est null", 
            retroplayFile.exists())
    }
}













