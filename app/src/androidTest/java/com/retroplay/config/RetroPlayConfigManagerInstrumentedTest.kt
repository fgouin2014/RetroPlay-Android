package com.retroplay.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import java.io.File

/**
 * Tests instrumentés pour RetroPlayConfigManager
 * Ces tests s'exécutent sur un device/émulateur Android et peuvent accéder au système de fichiers.
 * 
 * Vérifie que les fichiers .cfg sont créés, lus et sauvegardés correctement
 * pour les cores et les jeux.
 */
@RunWith(AndroidJUnit4::class)
class RetroPlayConfigManagerInstrumentedTest {
    
    private val testConsole = "nes"
    private val testGameName = "Duck Hunt"
    private val testCorePath = "fceumm_libretro_android.so"
    
    private lateinit var testBaseDir: File
    
    @Before
    fun setUp() {
        // Utiliser le contexte de l'app pour obtenir un répertoire accessible
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testBaseDir = File(context.getExternalFilesDir(null), "test_config")
        testBaseDir.mkdirs()
        
        // Configurer RetroPlayConfigManager pour utiliser le répertoire de test
        RetroPlayConfigManager.setTestBaseDir(testBaseDir.absolutePath)
        
        // Nettoyer les fichiers de test avant chaque test
        cleanupTestFiles()
    }
    
    @After
    fun tearDown() {
        // Nettoyer les fichiers de test après chaque test
        cleanupTestFiles()
        
        // Réinitialiser le baseDir à null pour ne pas affecter d'autres tests
        RetroPlayConfigManager.setTestBaseDir(null)
    }
    
    private fun cleanupTestFiles() {
        val configDir = File(testBaseDir, testConsole)
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
        val expectedPath = File(testBaseDir, "$testConsole/fceumm.cfg")
        val configFile = File(expectedPath.absolutePath)
        
        assertTrue("Le fichier de config doit être créé: ${configFile.absolutePath}", configFile.exists())
        assertNotNull("La config doit être retournée", config)
    }
    
    @Test
    fun testLoadConfig_UsesCoreName() {
        // Test: Le fichier de config doit utiliser le nom du core, pas "retroplay.cfg"
        RetroPlayConfigManager.loadConfig(testConsole, testCorePath)
        
        val coreConfigFile = File(testBaseDir, "$testConsole/fceumm.cfg")
        
        assertTrue("Le fichier fceumm.cfg doit exister", coreConfigFile.exists())
        
        // Vérifier que le contenu contient des valeurs par défaut
        val content = coreConfigFile.readText()
        assertTrue("Le fichier doit contenir des configurations", content.isNotEmpty())
        assertTrue("Le fichier doit contenir run_ahead_enabled", content.contains("run_ahead_enabled"))
    }
    
    @Test
    fun testSaveConfig_CreatesFileWithCoreName() {
        // Test: Sauvegarder une config doit créer le fichier avec le nom du core
        val config = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = true,
            runAheadFrames = 2
        )
        
        RetroPlayConfigManager.saveConfig(testConsole, config, testCorePath)
        
        val expectedPath = File(testBaseDir, "$testConsole/fceumm.cfg")
        val configFile = File(expectedPath.absolutePath)
        
        assertTrue("Le fichier doit être créé", configFile.exists())
        
        // Vérifier le contenu
        val content = configFile.readText()
        assertTrue("Le fichier doit contenir run_ahead_enabled", content.contains("run_ahead_enabled"))
        assertTrue("Le fichier doit contenir la valeur true", content.contains("true"))
        assertTrue("Le fichier doit contenir run_ahead_frames = \"2\"", content.contains("run_ahead_frames = \"2\""))
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
        val expectedPath = File(testBaseDir, "$testConsole/Duck_Hunt.cfg")
        val gameConfigFile = File(expectedPath.absolutePath)
        
        assertTrue("Le fichier per-game doit être créé: ${gameConfigFile.absolutePath}", gameConfigFile.exists())
        
        // Vérifier le contenu
        val content = gameConfigFile.readText()
        assertTrue("Le fichier doit contenir zapper_enabled", content.contains("zapper_enabled"))
    }
    
    @Test
    fun testSaveGameConfig_SavesAllValues() {
        // Test: Toutes les valeurs doivent être sauvegardées explicitement (sans commentaires)
        val globalConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = false,
            runAheadFrames = 1,
            zapperEnabled = true
        )
        
        val gameConfig = RetroPlayConfigManager.RetroPlayConfig(
            runAheadEnabled = false,  // Identique à global
            runAheadFrames = 2,       // Différent de global
            zapperEnabled = false      // Différent de global
        )
        
        // Sauvegarder la config globale d'abord
        RetroPlayConfigManager.saveConfig(testConsole, globalConfig, testCorePath)
        
        // Sauvegarder la config per-game
        RetroPlayConfigManager.saveGameConfig(testConsole, testGameName, gameConfig, testCorePath)
        
        val gameConfigFile = File(testBaseDir, "$testConsole/Duck_Hunt.cfg")
        assertTrue("Le fichier per-game doit exister", gameConfigFile.exists())
        
        val content = gameConfigFile.readText()
        
        // Vérifier que toutes les valeurs sont sauvegardées explicitement (sans commentaires)
        assertTrue("run_ahead_enabled doit être sauvegardé sans commentaire", 
            content.contains("run_ahead_enabled = \"false\"") && !content.contains("# run_ahead_enabled"))
        
        assertTrue("run_ahead_frames doit être sauvegardé sans commentaire", 
            content.contains("run_ahead_frames = \"2\"") && !content.contains("# run_ahead_frames"))
        
        assertTrue("zapper_enabled doit être sauvegardé sans commentaire", 
            content.contains("zapper_enabled = \"false\"") && !content.contains("# zapper_enabled"))
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
            "snes9x_libretro_android.so" to "snes9x",
            "pcsx_rearmed_libretro_android.so" to "pcsx_rearmed",
            "parallel_n64_libretro_android.so" to "parallel_n64"
        )
        
        testCases.forEach { (corePath, expectedCoreName) ->
            val config = RetroPlayConfigManager.RetroPlayConfig()
            RetroPlayConfigManager.saveConfig("test_console", config, corePath)
            
            // Le fichier doit être créé avec le nom du core
            val expectedFile = File(testBaseDir, "test_console/$expectedCoreName.cfg")
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
        
        val retroplayFile = File(testBaseDir, "$testConsole/retroplay.cfg")
        assertTrue("Le fichier retroplay.cfg doit être créé quand corePath est null", 
            retroplayFile.exists())
    }
    
    @Test
    fun testGameNameSanitization() {
        // Test: Les noms de jeux avec caractères spéciaux doivent être sanitized
        val specialGameName = "Game: Test (2024)!"
        val config = RetroPlayConfigManager.RetroPlayConfig()
        
        RetroPlayConfigManager.saveGameConfig(testConsole, specialGameName, config, testCorePath)
        
        // Le nom doit être sanitized (caractères spéciaux remplacés par _)
        val sanitizedPath = File(testBaseDir, "$testConsole/Game__Test__2024__.cfg")
        val gameConfigFile = File(sanitizedPath.absolutePath)
        
        assertTrue("Le fichier doit être créé avec le nom sanitized", gameConfigFile.exists())
        
        // Nettoyer
        gameConfigFile.delete()
    }
}













