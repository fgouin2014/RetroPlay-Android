package com.retroplay;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.content.Intent;
import android.util.Log;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Activity pour afficher les détails d'un jeu
 */
public class GameDetailsActivity extends AppCompatActivity {
    private static final String TAG = "GameDetailsActivity";
    
    private Game game;
    private ImageView gameImage;
    private ImageView gameScreenshot;
    private TextView gameTitle;
    private TextView gameDescription;
    private TextView gameGenre;
    private TextView gamePlayers;
    private TextView gameReleaseDate;
    private Button playButton;
    private MaterialButton playNativeButton;
    private MaterialButton loadSaveButton;
    private MaterialButton cheatButton;
    private MaterialButton coreOverrideButton;
    private LinearLayout nativeButtonsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Mode plein écran - masquer barre d'état et navigation
        setupFullscreenMode();
        
        setContentView(R.layout.activity_game_details_modern);
        
        // Récupérer le jeu depuis l'intent
        game = (Game) getIntent().getSerializableExtra("game");
        if (game == null) {
            Log.e(TAG, "Aucun jeu reçu dans l'intent");
            finish();
            return;
        }
        
        Log.i(TAG, "Affichage des détails pour: " + game.getName());
        
        setupToolbar();
        setupViews();
        populateGameDetails();
        setupButtons();
    }
    
    private void setupFullscreenMode() {
        // Masquer la barre d'état et la navigation
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        // Garder l'écran allumé
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Ne pas masquer la barre d'action car on utilise une Toolbar
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Set toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(game.getName());
        }
    }
    
    private void setupViews() {
        gameImage = findViewById(R.id.game_image);
        gameTitle = findViewById(R.id.game_title);
        gameDescription = findViewById(R.id.game_description);
        gameGenre = findViewById(R.id.game_genre);
        gamePlayers = findViewById(R.id.game_players);
        gameReleaseDate = findViewById(R.id.game_release_date);
        playButton = findViewById(R.id.play_button);
        playNativeButton = findViewById(R.id.play_native_button);
        loadSaveButton = findViewById(R.id.load_save_button);
        cheatButton = findViewById(R.id.cheat_button);
        coreOverrideButton = findViewById(R.id.core_override_button);
        nativeButtonsContainer = findViewById(R.id.native_buttons_container);
    }
    
           private void populateGameDetails() {
               // Titre du jeu
               gameTitle.setText(game.getName());
               gameTitle.setTypeface(null, Typeface.BOLD);
               
               // Description
               gameDescription.setText(game.getDesc());
               
               // Genre
               gameGenre.setText(game.getGenre());
               
               // Nombre de joueurs
               gamePlayers.setText("👥 " + game.getPlayers() + "P");
               
               // Date de sortie
               String releaseDate = formatReleaseDate(game.getReleasedate());
               gameReleaseDate.setText(releaseDate);
               
               // Images
               loadGameImages();
           }
    
    private void loadGameImages() {
        // Screenshot image (top)
        ImageView screenshotBackground = findViewById(R.id.game_screenshot_background);
        Glide.with(this)
                .load(game.getScreenshotWithFallback())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(screenshotBackground);
        
        // Box2D image (bottom) - icône avec fallback
        Glide.with(this)
                .load(game.getImageWithFallback())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(gameImage);
    }
    
    private void setupButtons() {
        // Play button (WASM)
        playButton.setOnClickListener(v -> launchGame());
        
        // Play native button (cores natifs)
        playNativeButton.setOnClickListener(v -> launchGameNative(0));  // 0 = nouvelle partie
        
        // Load save button (ouvre menu de sélection de slot)
        loadSaveButton.setOnClickListener(v -> showSlotSelectionDialog());
        
        // Cheat button (ouvre interface de codes de triche)
        cheatButton.setOnClickListener(v -> openCheatActivity());
        
        // Core override button (change le core utilisé pour ce jeu)
        coreOverrideButton.setOnClickListener(v -> showCoreOverrideDialog());
        updateCoreOverrideButton();
        
        // Afficher les boutons natifs pour TOUTES les consoles
        // L'utilisateur peut maintenant choisir entre WASM et NATIVE pour n'importe quelle console
        String console = game.getConsole().toLowerCase();
        nativeButtonsContainer.setVisibility(View.VISIBLE);
        
        // Vérifier si des sauvegardes existent dans les slots
        checkAndShowLoadSaveButton();
        
        // Floating action button
        FloatingActionButton fabPlay = findViewById(R.id.fabPlay);
        fabPlay.setOnClickListener(v -> launchGame());
        
        // Favorite button
        MaterialButton favoriteButton = findViewById(R.id.favorite_button);
        favoriteButton.setOnClickListener(v -> toggleFavorite());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Rafraîchir le bouton de core override au cas où il aurait été changé
        // depuis NativeComposeEmulatorActivity (via le dialog d'erreur)
        updateCoreOverrideButton();
    }
    
    private void launchGame() {
        Log.i(TAG, "Lancement du jeu (WASM): " + game.getName());
        
        // Get console configuration (options avancées non accessibles dans EmulatorJS GUI)
        ConsoleConfigActivity.ConsoleConfig config = ConsoleConfigActivity.getConfig(this, game.getConsole());
        
        // Get core override if exists
        String fileName = game.getFile();
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        String consoleDir = getRealConsoleDirectory(game.getConsole());
        String relativePath = consoleDir + "/" + fileName;
        
        CoreOverrideManager manager = CoreOverrideManager.getInstance();
        String coreOverride = null;
        if (manager.hasOverride(relativePath)) {
            CoreOverride override = manager.getOverride(relativePath);
            coreOverride = override.getCoreId();
            Log.i(TAG, "Using core override for EmulatorJS: " + coreOverride);
        }
        
        // PSP utilise Chrome Custom Tabs pour SharedArrayBuffer et multi-threading
        if (game.getConsole().equals("psp")) {
            Log.i(TAG, "Launching PSP with Chrome Custom Tabs (for threads support)");
            launchWithCustomTabs(config, coreOverride);
        } else {
            // Autres consoles utilisent WebView
            Log.i(TAG, "Launching with WebView");
            Intent intent = new Intent(this, WebViewActivity.class);
            intent.putExtra("file", game.getFile());
            intent.putExtra("gameName", game.getName());
            intent.putExtra("console", game.getConsole());
            intent.putExtra("touchScale", config.touchScale);
            intent.putExtra("touchAlpha", config.touchAlpha);
            // Pass core override to WebView
            if (coreOverride != null) {
                intent.putExtra("core", coreOverride);
            }
            // PSX D-Pad option
            if (config.useDpad && (game.getConsole().equals("psx") || game.getConsole().equals("ps1") || game.getConsole().equals("playstation"))) {
                intent.putExtra("useDpad", true);
            }
            startActivity(intent);
        }
    }
    
    /**
     * Mappe le nom de console au vrai nom de repertoire sur le device
     * Resout les problemes de duplication (lynx/atarilynx, sms/mastersystem, etc.)
     */
    private String getRealConsoleDirectory(String consoleName) {
        String console = consoleName.toLowerCase();
        
        // Utiliser les noms de repertoires REELS sur le device
        switch (console) {
            // Atari - Utiliser les noms complets
            case "lynx":
                return "atarilynx";  // Repertoire reel
            case "atarilynx":
                return "atarilynx";
            case "atari":
            case "a2600":
                return "atari2600";
            case "atari2600":
                return "atari2600";
            case "a5200":
                return "atari5200";
            case "atari5200":
                return "atari5200";
            case "a7800":
                return "atari7800";
            case "atari7800":
                return "atari7800";
                
            // Sega - Mapper aux noms de repertoires
            case "genesis":
            case "md":
                return "megadrive";  // Repertoire reel
            case "megadrive":
                return "megadrive";
            case "scd":
                return "segacd";
            case "segacd":
                return "segacd";
            case "mastersystem":
            case "segasms":
                return "mastersystem";  // Ou "sms" selon ce qui existe
            case "sms":
                return "sms";
            case "gamegear":
            case "segagg":
                return "gamegear";  // Ou "gg"
            case "gg":
                return "gamegear";
            case "32x":
            case "sega32x":
                return "32x";
                
            // Nintendo - Noms standards
            case "nes":
                return "nes";
            case "snes":
                return "snes";
            case "n64":
                return "n64";
            case "gb":
                return "gb";
            case "gbc":
                return "gbc";
            case "gba":
                return "gba";
                
            // Sony
            case "psx":
            case "ps1":
            case "playstation":
                return "psx";
            case "psp":
                return "psp";
                
            // Other
            case "ngp":
            case "ngc":
            case "neogeopocket":
                return "ngp";
            case "ws":
            case "wsc":
            case "wonderswan":
                return "ws";
            case "pce":
            case "turbografx":
            case "pcengine":
                return "pce";
            case "arcade":
                return "arcade";
            case "mame":
                return "mame";  // Repertoire MAME separe
            case "fbneo":
            case "neogeo":
            case "cps1":
            case "cps2":
                return "fbneo";  // Repertoire reel
            case "vb":
            case "virtualboy":
                return "virtualboy";  // Ou "vb"
            case "jaguar":
                return "jaguar";
            case "saturn":
                return "saturn";
            case "3do":
                return "3do";
            case "nds":
                return "nds";
                
            // Default: utiliser tel quel
            default:
                return console;
        }
    }
    
    private long lastNativeLaunchTime = 0;
    private static final long MIN_NATIVE_LAUNCH_INTERVAL_MS = 1000; // Minimum 1 seconde entre deux lancements natifs
    
    private void launchGameNative(int slot) {
        // Vérifier le délai minimum entre deux lancements natifs
        // Nécessaire car LibretroDroid ne peut pas charger le même core si l'ancien n'est pas libéré
        long currentTime = System.currentTimeMillis();
        long timeSinceLastLaunch = currentTime - lastNativeLaunchTime;
        
        if (timeSinceLastLaunch < MIN_NATIVE_LAUNCH_INTERVAL_MS) {
            long remainingDelay = MIN_NATIVE_LAUNCH_INTERVAL_MS - timeSinceLastLaunch;
            Log.w(TAG, "⏳ Too fast! Waiting " + remainingDelay + "ms before launching (core cleanup)");
            
            Toast.makeText(this, "Please wait...", Toast.LENGTH_SHORT).show();
            
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                launchGameNative(slot);
            }, remainingDelay);
            return;
        }
        
        lastNativeLaunchTime = currentTime;
        
        String slotInfo = (slot == 0) ? "[NEW GAME]" : "[LOAD SLOT " + slot + "]";
        Log.i(TAG, "Lancement du jeu (NATIVE COMPOSE): " + game.getName() + " " + slotInfo);
        
        // Extraire le nom du fichier depuis l'URL ou utiliser tel quel
        String fileName = game.getFile();
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            // Extraire juste le nom du fichier depuis l'URL
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        
        // Construire le chemin complet vers la ROM
        // Mapper le nom de console au vrai nom de répertoire sur le device
        String consoleDir = getRealConsoleDirectory(game.getConsole());
        String romPath = "/storage/emulated/0/GameLibrary-Data/" + consoleDir + "/" + fileName;
        
        // DETECTION INTELLIGENTE DES FORMATS (comme PSX)
        String console = game.getConsole().toLowerCase();
        android.content.SharedPreferences prefs = getSharedPreferences("compose_gamepad_settings", MODE_PRIVATE);
        
        // Formats compressés natifs supportés par les cores (comme PSX .pbp, .chd)
        // Ces formats ne nécessitent PAS d'extraction
        boolean isNativeCompressedFormat = 
            fileName.endsWith(".pbp") ||   // PSX PSP format
            fileName.endsWith(".chd") ||   // Compressed Hunks of Data (PSX, SegaCD, Saturn, etc.)
            fileName.endsWith(".cso") ||   // PSP compressed ISO
            fileName.endsWith(".daa");     // PowerISO compressed
        
        // Exception: FBNeo/Arcade/MAME ROMs en .zip ne doivent PAS être extraites
        // Le core lit les .zip directement (ROM sets MAME)
        // Supporter aussi les sous-consoles (fbneo/sega, fbneo/taito, etc.)
        boolean isArcadeZip = (console.toLowerCase().startsWith("fbneo") || 
                               console.equals("arcade") || 
                               console.equals("mame") || 
                               console.equals("neogeo")) && 
                              fileName.endsWith(".zip");
        
        // Archives nécessitant extraction (.zip, .7z comme Lemuroid)
        // TOUTES les consoles sauf arcade doivent extraire les .zip
        boolean isArchive = (fileName.endsWith(".zip") || fileName.endsWith(".7z")) && !isArcadeZip;
        
        if (isNativeCompressedFormat) {
            Log.i(TAG, console + ": Native compressed format detected, loading directly: " + fileName);
            // Charger directement sans extraction (le core supporte ce format)
        } else if (isArcadeZip) {
            Log.i(TAG, console + ": Arcade ROM .zip detected, loading directly (core reads .zip natively)");
            // Charger directement (les ROMs arcade sont en .zip et ne doivent PAS être extraites)
        } else if (isArchive) {
            // TOUTES les consoles (NES, SNES, etc.) nécessitent l'extraction du .zip
            // Par DEFAUT: cache ACTIVE pour les archives (comme Lemuroid/EmulatorJS)
            // L'utilisateur peut le desactiver manuellement dans les parametres si besoin
            boolean cacheEnabled = prefs.getBoolean("cache_enabled_" + console, true);  // TRUE par défaut
            
            if (cacheEnabled) {
                Log.i(TAG, console + ": Archive detected (" + fileName + "), extracting to cache...");
                extractToCacheAsync(romPath, fileName, slot, console);
                return;  // L'extraction lancera l'Activity une fois terminee
            } else {
                Log.w(TAG, console + ": Cache disabled by user, trying archive directly (WILL NOT WORK)");
                // Continuer quand même mais ça ne fonctionnera probablement pas
            }
        }
        
        Log.i(TAG, "ROM path: " + romPath);
        
        Intent intent = new Intent(this, NativeComposeEmulatorActivity.class);
        intent.putExtra("romPath", romPath);
        intent.putExtra("gameName", game.getName());
        intent.putExtra("console", game.getConsole());
        intent.putExtra("loadSlot", slot);  // 0 = nouvelle partie, 1-5 = charger slot
        
        startActivity(intent);
    }
    
    /**
     * Extrait un .zip dans le cache en arriere-plan (evite ANR)
     * Toutes les consoles supportees (optionnel, active par l'utilisateur)
     * Par defaut: DESACTIVE (charge ROM directement)
     * Si probleme: L'utilisateur active le cache dans le menu
     */
    private void extractToCacheAsync(final String zipPath, final String zipFileName, final int slot, final String console) {
        // Mapper au vrai nom de répertoire
        final String realConsoleDir = getRealConsoleDirectory(console);
        
        // Repertoire de cache par console (utiliser le vrai nom de répertoire)
        final String cacheDir = "/storage/emulated/0/GameLibrary-Data/.cache/" + realConsoleDir;
        
        // Determiner l'extension cible selon la console
        final String targetExtension;
        switch (console) {
            // Atari
            case "lynx":
            case "atarilynx":
                targetExtension = ".lnx";
                break;
            case "atari2600":
            case "atari":
            case "a2600":
                targetExtension = ".a26";
                break;
            case "atari5200":
            case "a5200":
                targetExtension = ".a52";
                break;
            case "atari7800":
            case "a7800":
                targetExtension = ".a78";
                break;
            
            // Nintendo (accepter aussi .bin comme fallback)
            case "nes":
                targetExtension = ".nes";
                break;
            case "snes":
                targetExtension = ".sfc";  // Ou .smc
                break;
            case "n64":
                targetExtension = ".z64";  // Ou .n64, .v64
                break;
            case "gb":
                targetExtension = ".gb";
                break;
            case "gbc":
                targetExtension = ".gbc";
                break;
            case "gba":
                targetExtension = ".gba";
                break;
            
            // Sega
            case "genesis":
            case "megadrive":
            case "md":
                targetExtension = ".bin";  // Ou .smd, .md, .gen
                break;
            case "mastersystem":
            case "sms":
            case "segasms":
                targetExtension = ".sms";
                break;
            case "gamegear":
            case "gg":
            case "segagg":
                targetExtension = ".gg";
                break;
            case "32x":
            case "sega32x":
                targetExtension = ".32x";
                break;
            
            // Other
            case "ngp":
            case "ngc":
            case "neogeopocket":
                targetExtension = ".ngp";
                break;
            case "ws":
            case "wsc":
            case "wonderswan":
                targetExtension = ".ws";
                break;
            case "pce":
            case "turbografx":
            case "pcengine":
                targetExtension = ".pce";
                break;
            
            // Arcade (FBNeo) - garde .zip (ROM sets)
            case "fbneo":
            case "neogeo":
            case "cps1":
            case "cps2":
            case "arcade":
            case "mame":
                targetExtension = ".zip";  // Les ROMs arcade restent en .zip
                break;
            
            // Default
            default:
                targetExtension = ".bin";
                break;
        }
        
        // Nom du fichier extrait (sans region)
        final String simpleName = game.getName().replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
        final String cachedRomPath = cacheDir + "/" + simpleName + targetExtension;
        
        // Verifier si deja en cache (rapide, sur UI thread)
        java.io.File cachedRomFile = new java.io.File(cachedRomPath);
        if (cachedRomFile.exists()) {
            Log.i(TAG, console + ": Using cached ROM: " + cachedRomPath);
            launchWithCachedRom(cachedRomPath, slot);
            return;
        }
        
        // Afficher dialogue de progression
        final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Extracting " + console.toUpperCase() + " ROM...");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Extraire en arriere-plan
        new Thread(() -> {
            try {
                // Creer repertoire cache
                java.io.File cacheDirFile = new java.io.File(cacheDir);
                if (!cacheDirFile.exists()) {
                    cacheDirFile.mkdirs();
                }
                
                // Extraire archive (.zip, .7z comme Lemuroid)
                java.io.File archiveFile = new java.io.File(zipPath);
                if (!archiveFile.exists()) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "ROM archive not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                boolean extracted = false;
                
                // Traitement différent selon le type d'archive
                if (zipPath.endsWith(".7z")) {
                    // .7z nécessite SevenZFile (pas de streaming)
                    org.apache.commons.compress.archivers.sevenz.SevenZFile sevenZFile = 
                        new org.apache.commons.compress.archivers.sevenz.SevenZFile(archiveFile);
                    
                    org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry;
                    while ((entry = sevenZFile.getNextEntry()) != null) {
                        String entryName = entry.getName().toLowerCase();
                        
                        if (isValidRomFormat(entryName, console)) {
                            // Lire depuis sevenZFile
                            java.io.FileOutputStream out = new java.io.FileOutputStream(cachedRomFile);
                            
                            byte[] buffer = new byte[65536];
                            int bytesRead;
                            while ((bytesRead = sevenZFile.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                            
                            out.close();
                            extracted = true;
                            Log.i(TAG, console + ": Found ROM in .7z: " + entryName);
                            break;
                        }
                    }
                    
                    sevenZFile.close();
                    
                } else {
                    // .zip avec java.util.zip.ZipFile (simple et rapide)
                    java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(archiveFile);
                    java.util.Enumeration<? extends java.util.zip.ZipEntry> zipEntries = zipFile.entries();
                    
                    while (zipEntries.hasMoreElements()) {
                        java.util.zip.ZipEntry zipEntry = zipEntries.nextElement();
                        String entryName = zipEntry.getName().toLowerCase();
                        
                        if (isValidRomFormat(entryName, console)) {
                            // Lire depuis zipFile
                            java.io.InputStream in = zipFile.getInputStream(zipEntry);
                            java.io.FileOutputStream out = new java.io.FileOutputStream(cachedRomFile);
                            
                            byte[] buffer = new byte[65536];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                            
                            out.close();
                            in.close();
                            extracted = true;
                            Log.i(TAG, console + ": Found ROM in .zip: " + entryName);
                            break;
                        }
                    }
                    
                    zipFile.close();
                }
                
                if (extracted) {
                    Log.i(TAG, console + ": Extracted to cache: " + cachedRomPath);
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        launchWithCachedRom(cachedRomPath, slot);
                    });
                } else {
                    Log.w(TAG, console + ": No ROM file in .zip, trying .zip directly");
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        launchWithCachedRom(zipPath, slot);  // Fallback au .zip
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, console + ": Error extracting .zip", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error extracting ROM", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * Verifie si une extension de fichier est valide pour une console donnee
     */
    private boolean isValidRomFormat(String entryName, String console) {
        // Lynx
        if (console.equals("lynx") || console.equals("atarilynx")) {
            return entryName.endsWith(".lnx");
        }
        // Atari 2600
        else if (console.equals("atari2600") || console.equals("atari") || console.equals("a2600")) {
            return entryName.endsWith(".a26") || entryName.endsWith(".bin");
        }
        // Atari 5200
        else if (console.equals("atari5200") || console.equals("a5200")) {
            return entryName.endsWith(".a52") || entryName.endsWith(".bin");
        }
        // Atari 7800
        else if (console.equals("atari7800") || console.equals("a7800")) {
            return entryName.endsWith(".a78") || entryName.endsWith(".bin");
        }
        // NES
        else if (console.equals("nes")) {
            return entryName.endsWith(".nes") || entryName.endsWith(".fds") || entryName.endsWith(".unf");
        }
        // SNES
        else if (console.equals("snes")) {
            return entryName.endsWith(".sfc") || entryName.endsWith(".smc");
        }
        // N64
        else if (console.equals("n64")) {
            return entryName.endsWith(".z64") || entryName.endsWith(".n64") || entryName.endsWith(".v64");
        }
        // GB
        else if (console.equals("gb")) {
            return entryName.endsWith(".gb") || entryName.endsWith(".sgb");
        }
        // GBC
        else if (console.equals("gbc")) {
            return entryName.endsWith(".gbc") || entryName.endsWith(".gb");
        }
        // GBA
        else if (console.equals("gba")) {
            return entryName.endsWith(".gba") || entryName.endsWith(".agb");
        }
        // Genesis / MegaDrive
        else if (console.equals("genesis") || console.equals("megadrive") || console.equals("md")) {
            return entryName.endsWith(".bin") || entryName.endsWith(".smd") || 
                  entryName.endsWith(".md") || entryName.endsWith(".gen");
        }
        // Master System
        else if (console.equals("mastersystem") || console.equals("sms") || console.equals("segasms")) {
            return entryName.endsWith(".sms") || entryName.endsWith(".bin");
        }
        // Game Gear
        else if (console.equals("gamegear") || console.equals("gg") || console.equals("segagg")) {
            return entryName.endsWith(".gg") || entryName.endsWith(".bin");
        }
        // 32X
        else if (console.equals("32x") || console.equals("sega32x")) {
            return entryName.endsWith(".32x") || entryName.endsWith(".bin");
        }
        // Neo Geo Pocket
        else if (console.equals("ngp") || console.equals("ngc") || console.equals("neogeopocket")) {
            return entryName.endsWith(".ngp") || entryName.endsWith(".ngc");
        }
        // WonderSwan
        else if (console.equals("ws") || console.equals("wsc") || console.equals("wonderswan")) {
            return entryName.endsWith(".ws") || entryName.endsWith(".wsc");
        }
        // PC Engine
        else if (console.equals("pce") || console.equals("turbografx") || console.equals("pcengine")) {
            return entryName.endsWith(".pce") || entryName.endsWith(".sgx");
        }
        // Arcade (FBNeo) - ROMs en .zip (ROM sets)
        else if (console.equals("fbneo") || console.equals("arcade") || console.equals("mame") || 
                 console.equals("neogeo") || console.equals("cps1") || console.equals("cps2")) {
            return entryName.endsWith(".zip");  // Les ROMs arcade sont en .zip
        }
        // Fallback générique
        else {
            return entryName.endsWith(".bin") || entryName.endsWith(".rom");
        }
    }
    
    /**
     * Lance l'emulateur avec une ROM deja extraite ou en cache
     */
    private void launchWithCachedRom(String romPath, int slot) {
        Intent intent = new Intent(this, NativeComposeEmulatorActivity.class);
        intent.putExtra("romPath", romPath);
        intent.putExtra("gameName", game.getName());
        intent.putExtra("console", game.getConsole());
        intent.putExtra("loadSlot", slot);
        startActivity(intent);
    }
    
    private void openCheatActivity() {
        Log.i(TAG, "Opening cheat codes for: " + game.getName());
        
        Intent intent = new Intent(this, com.retroplay.cheat.CheatActivity.class);
        intent.putExtra("console", game.getConsole());
        intent.putExtra("gameName", game.getName());
        startActivity(intent);
    }
    
    private void showSlotSelectionDialog() {
        String console = game.getConsole();
        String gameName = game.getName();
        
        // Créer une liste des slots avec infos
        String[] slotLabels = new String[5];
        for (int slot = 1; slot <= 5; slot++) {
            java.io.File saveFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + slot + "/" + gameName + ".state");
            
            if (saveFile.exists()) {
                long lastModified = saveFile.lastModified();
                long sizeKB = saveFile.length() / 1024;
                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                String dateStr = dateFormat.format(new java.util.Date(lastModified));
                
                slotLabels[slot - 1] = "Slot " + slot + " - " + dateStr + " (" + sizeKB + "KB)";
            } else {
                slotLabels[slot - 1] = "Slot " + slot + " [Empty]";
            }
        }
        
        // Afficher le dialogue de sélection
        new AlertDialog.Builder(this)
                .setTitle("Load Game - " + console.toUpperCase())
                .setItems(slotLabels, (dialog, which) -> {
                    int selectedSlot = which + 1;
                    
                    // Vérifier si le slot existe
                    java.io.File saveFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + selectedSlot + "/" + gameName + ".state");
                    if (saveFile.exists()) {
                        launchGameNative(selectedSlot);
                    } else {
                        Toast.makeText(this, "No save in Slot " + selectedSlot, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void checkAndShowLoadSaveButton() {
        // Vérifier si des sauvegardes existent dans les slots
        String console = game.getConsole();
        String gameName = game.getName();
        
        boolean hasAnySave = false;
        for (int slot = 1; slot <= 5; slot++) {
            java.io.File saveFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + slot + "/" + gameName + ".state");
            if (saveFile.exists()) {
                hasAnySave = true;
                break;
            }
        }
        
        if (hasAnySave) {
            loadSaveButton.setVisibility(View.VISIBLE);
            Log.i(TAG, "Save slots found for: " + gameName);
        } else {
            loadSaveButton.setVisibility(View.GONE);
            Log.d(TAG, "No save slots found for: " + gameName);
        }
    }
    
    private void launchWithCustomTabs(ConsoleConfigActivity.ConsoleConfig config, String coreOverride) {
        try {
            // Custom Tabs s'exécute sur le device, donc on utilise TOUJOURS localhost
            // Cela permet d'activer les threads (COEP/COOP headers acceptés sur localhost)
            String gameSlug = generateSlug(game.getName());
            
            // Construire l'URL avec option D-Pad si activée (comme PSX)
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("http://localhost:7777/gamelibrary/emulator.html?slug=").append(gameSlug);
            urlBuilder.append("&console=").append(game.getConsole());
            
            if (config.useDpad) {
                urlBuilder.append("&dpad=true");
                Log.i(TAG, "PSP with D-Pad controls (no analog sticks)");
            } else {
                Log.i(TAG, "PSP with Analog controls (DualShock with sticks)");
            }
            
            // Add core override if exists
            if (coreOverride != null) {
                urlBuilder.append("&core=").append(coreOverride);
                Log.i(TAG, "Using core override for EmulatorJS: " + coreOverride);
            }
            
            String emulatorUrl = urlBuilder.toString();
            Log.i(TAG, "Launching fullscreen Custom Tabs: " + emulatorUrl);
            
            // Lancer via FullscreenCustomTabsActivity pour rotation libre et mode immersif
            Intent intent = new Intent(this, FullscreenCustomTabsActivity.class);
            intent.putExtra("url", emulatorUrl);
            startActivity(intent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching Custom Tabs: " + e.getMessage());
            Toast.makeText(this, "Erreur lors du lancement: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String generateSlug(String name) {
        return name.toLowerCase()
            .replaceAll("['\"`]", "")
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
    
    private void toggleFavorite() {
        // TODO: Implement favorite functionality
        Log.i(TAG, "Toggle favorite for: " + game.getName());
    }
    
    /**
     * Met à jour le texte du bouton core override selon l'état actuel
     */
    private void updateCoreOverrideButton() {
        String fileName = game.getFile();
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        
        String consoleDir = getRealConsoleDirectory(game.getConsole());
        String relativePath = consoleDir + "/" + fileName;
        
        CoreOverrideManager manager = CoreOverrideManager.getInstance();
        if (manager.hasOverride(relativePath)) {
            CoreOverride override = manager.getOverride(relativePath);
            coreOverrideButton.setText("⚡ " + override.getCoreId().toUpperCase());
        } else {
            // Afficher le core par défaut basé sur la console
            String defaultCore = getDefaultCoreForConsole(game.getConsole());
            coreOverrideButton.setText("⚙ " + defaultCore);
        }
    }
    
    /**
     * Obtient le nom du core par défaut pour une console
     */
    private String getDefaultCoreForConsole(String console) {
        String consoleKey = console.toLowerCase();
        
        // Pour les sous-consoles, utiliser le parent
        if (consoleKey.contains("/")) {
            consoleKey = consoleKey.substring(0, consoleKey.indexOf("/"));
        }
        
        switch (consoleKey) {
            case "nes": return "FCEUmm";
            case "snes": return "Snes9x";
            case "n64": return "ParaLLEl N64";
            case "gb":
            case "gbc": return "Gambatte";
            case "gba": return "mGBA";
            case "psx":
            case "ps1":
            case "playstation": return "PCSX ReARMed";
            case "psp": return "PPSSPP";
            case "genesis":
            case "megadrive":
            case "md":
            case "scd":
            case "segacd":
            case "mastersystem":
            case "sms":
            case "gamegear":
            case "gg": return "Genesis Plus GX";
            case "32x":
            case "sega32x": return "PicoDrive";
            case "atari2600":
            case "a2600": return "Stella 2014";
            case "atari5200":
            case "a5200": return "Atari800";
            case "atari7800":
            case "a7800": return "ProSystem";
            case "atarilynx":
            case "lynx": return "Handy";
            case "ngp":
            case "ngpc": return "Mednafen NGP";
            case "wonderswan":
            case "ws": return "Mednafen WonderSwan";
            case "pce":
            case "pcengine": return "Mednafen PCE Fast";
            // Arcade
            case "arcade": return "FBNeo";
            case "mame": return "MAME 2010";
            case "fbneo": return "FBNeo";
            case "cps1": return "FBalpha CPS1";
            case "cps2": return "FBalpha CPS2";
            case "cps3": return "FBNeo";
            case "neogeo": return "FBNeo";
            default: return "Default";
        }
    }
    
    /**
     * Affiche un dialog pour choisir le core à utiliser pour ce jeu
     */
    private void showCoreOverrideDialog() {
        String fileName = game.getFile();
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        
        String consoleDir = getRealConsoleDirectory(game.getConsole());
        String relativePath = consoleDir + "/" + fileName;
        
        // Obtenir le core par défaut
        String defaultCoreName = getDefaultCoreForConsole(game.getConsole());
        
        // Liste des cores disponibles organisée par catégorie
        String[] cores = {
            "Default (" + defaultCoreName + ")",
            "── ARCADE CORES ──",
            "FBNeo (Arcade/Neo Geo)",
            "MAME 2010 (Arcade)",
            "MAME 2003 Plus (Arcade)",
            "MAME 2003 (Arcade)",
            "FBalpha CPS1 (Capcom)",
            "FBalpha CPS2 (Capcom)",
            "Flycast (Dreamcast Arcade)",
            "── CONSOLE CORES ──",
            "FCEUmm (NES)",
            "Snes9x (SNES)",
            "ParaLLEl N64 (N64)",
            "Mupen64Plus Next GLES3 (N64)",
            "Mupen64Plus Next GLES2 (N64)",
            "Gambatte (GB/GBC)",
            "mGBA (GBA)",
            "PCSX ReARMed (PSX)",
            "PPSSPP (PSP)",
            "Genesis Plus GX (Sega)",
            "PicoDrive (Sega 32X)"
        };
        
        String[] coreIds = {
            null,           // Default
            null,           // Header ARCADE
            "fbneo",
            "mame2010",
            "mame2003_plus",
            "mame2003",
            "fbalpha2012_cps1",
            "fbalpha2012_cps2",
            "flycast",
            null,           // Header CONSOLE
            "fceumm",
            "snes9x",
            "parallel_n64",
            "mupen64plus_next_gles3",
            "mupen64plus_next_gles2",
            "gambatte",
            "mgba",
            "pcsx_rearmed",
            "ppsspp",
            "genesis_plus_gx",
            "picodrive"
        };
        
        CoreOverrideManager manager = CoreOverrideManager.getInstance();

        // Déterminer quel item est actuellement sélectionné
        int tempSelectedPosition = -1;
        if (manager.hasOverride(relativePath)) {
            CoreOverride override = manager.getOverride(relativePath);
            String currentCoreId = override.getCoreId();
            for (int i = 0; i < coreIds.length; i++) {
                if (currentCoreId.equals(coreIds[i])) {
                    tempSelectedPosition = i;
                    break;
                }
            }
        } else {
            // Si pas d'override, c'est le "Default" qui est sélectionné (position 0)
            tempSelectedPosition = 0;
        }
        final int selectedPosition = tempSelectedPosition;

        // Créer un ListView personnalisé avec scrolling et checkmarks
        ListView listView = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, cores) {
            @Override
            public boolean isEnabled(int position) {
                // Désactiver les headers (indices 1 et 9)
                return position != 1 && position != 9;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);

                // Ajouter un checkmark si c'est l'item sélectionné
                String text = cores[position];
                if (position == selectedPosition) {
                    text = "✓ " + text;
                    textView.setTextColor(Color.parseColor("#4CAF50")); // Vert pour l'item sélectionné
                } else if (position == 1 || position == 9) {
                    // Style pour les headers
                    textView.setTextColor(Color.GRAY);
                    textView.setTextSize(12);
                    textView.setTypeface(null, Typeface.BOLD);
                    textView.setPadding(16, 16, 16, 8);
                } else {
                    textView.setTextColor(Color.BLACK);
                    textView.setTextSize(14);
                    textView.setTypeface(null, Typeface.NORMAL);
                    textView.setPadding(32, 8, 16, 8); // Padding gauche plus grand pour compenser le checkmark
                }
                textView.setText(text);

                return view;
            }
        };

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select Core for " + game.getName())
                .setView(listView)
                .setNegativeButton("Cancel", null)
                .create();

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            // Vérifier si c'est un header (indices 1 et 9)
            if (position == 1 || position == 9) {
                // Headers non cliquables, ne rien faire
                return;
            }

            if (position == 0) {
                // Default - supprimer l'override
                manager.removeOverride(relativePath);
                Toast.makeText(this, "Using default core: " + defaultCoreName, Toast.LENGTH_SHORT).show();
            } else {
                // Définir un override
                String coreId = coreIds[position];
                if (coreId != null) {
                    String reason = "User selected: " + cores[position];
                    manager.setOverride(relativePath, coreId, reason);
                    Toast.makeText(this, "Core set to: " + coreId.toUpperCase(), Toast.LENGTH_SHORT).show();
                }
            }
            updateCoreOverrideButton();
            dialog.dismiss();
        });

        dialog.show();
    }
    
    private String formatReleaseDate(String releaseDate) {
        try {
            // Format: 19881002T000000 -> 1988-10-02
            if (releaseDate.length() >= 8) {
                String year = releaseDate.substring(0, 4);
                String month = releaseDate.substring(4, 6);
                String day = releaseDate.substring(6, 8);
                return day + "/" + month + "/" + year;
            }
        } catch (Exception e) {
            Log.w(TAG, "Erreur formatage date: " + releaseDate, e);
        }
        return releaseDate;
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Maintenir le mode plein écran même après perte de focus
            setupFullscreenMode();
        }
    }
}
