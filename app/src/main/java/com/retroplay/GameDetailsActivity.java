package com.retroplay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Menu;
import android.view.MenuItem;
import android.content.res.ColorStateList;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.content.Intent;
import android.util.Log;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.retroplay.database.GameInfo;
import com.retroplay.gallery.ScreenshotRepository;
import java.io.File;
import androidx.core.content.ContextCompat;

/**
 * Activity pour afficher les détails d'un jeu
 */
public class GameDetailsActivity extends AppCompatActivity {
    private static final String TAG = "GameDetailsActivity";
    private static final String GAME_INFO_DIALOG_TAG = "game_info_dialog";
    private static final String GAME_INFO_DIALOG_RESULT_KEY = "game_info_dialog_result";
    private static final String GAME_INFO_DIALOG_EVENT_KEY = "event";
    private static final String GAME_INFO_DIALOG_EVENT_SHOW = "show";
    private static final String GAME_INFO_DIALOG_EVENT_DISMISS = "dismiss";
    
    private Game game;
    private ImageView gameImage;
    private ImageView gameScreenshot;
    private TextView gameTitle;
    private TextView gameDescription;
    private TextView gameGenre;
    private TextView gamePlayers;
    private TextView gameReleaseDate;
    private TextView gameRating;
    private Button playButton;
    private MaterialButton playNativeButton;
    private MaterialButton loadSaveButton;
    private MaterialButton cheatButton;
    private MaterialButton coreOverrideButton;
    private MaterialButton favoriteButton;
    private MaterialButton viewGalleryButton;
    private MaterialButton headerBackButton;
    private MaterialButton headerSettingsButton;
    private MaterialButton gameInfoButton;
    private TextView headerTitle;
    private FrameLayout pillWasm;
    private FrameLayout pillLoad;
    private FrameLayout pillNative;
    private LinearLayout nativeButtonsContainer;
    private FavoritesManager favoritesManager;
    
    private String currentGameCRC;
    private GameInfo currentGameInfo;
    private boolean isGameInfoDialogVisible;
    private String currentGalleryGameId;
    
    // Emulator Mode Toggle
    private SwitchMaterial emulatorModeSwitch;
    private TextView consoleDefaultInfo;
    private String currentConsole;
    private String currentGameId;
    
    // Database loading progress
    private android.app.ProgressDialog progressDialog;

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
        
        // Initialiser les variables console et gameId
        currentConsole = game.getConsole();
        currentGameId = game.getId();
        currentGalleryGameId = ScreenshotRepository.sanitizeGameKey(game.getName());
        
        // Initialiser le manager des favoris
        favoritesManager = FavoritesManager.getInstance(this);
        
        registerGameInfoDialogCallbacks();
        
        setupHeader();
        setupViews();
        populateGameDetails();
        setupButtons();
        setupEmulatorModeToggle();
        
        syncGameInfoDialogState();
        
        // Appliquer le thème
        applyTheme();
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
        
        // Laisser visible notre en-tête personnalisé
    }
    
    private void setupHeader() {
        headerBackButton = findViewById(R.id.headerBackButton);
        headerSettingsButton = findViewById(R.id.headerSettingsButton);
        gameInfoButton = findViewById(R.id.game_info_button);
        favoriteButton = findViewById(R.id.favorite_button);
        headerTitle = findViewById(R.id.headerTitle);
        
        headerTitle.setText(game.getName());
        headerBackButton.setOnClickListener(v -> finish());
        headerSettingsButton.setOnClickListener(this::showSettingsMenu);
        
        if (gameInfoButton != null) {
            gameInfoButton.setCheckable(true);
            gameInfoButton.setOnClickListener(v -> handleGameInfoAction());
            updateGameInfoButtonState();
        }
        
        if (favoriteButton != null) {
            updateFavoriteButton();
            favoriteButton.setOnClickListener(v -> toggleFavorite());
        }
    }
    
    private void setupViews() {
        gameImage = findViewById(R.id.game_image);
        gameTitle = findViewById(R.id.game_title);
        gameDescription = findViewById(R.id.game_description);
        gameGenre = findViewById(R.id.game_genre);
        gamePlayers = findViewById(R.id.game_players);
        gameReleaseDate = findViewById(R.id.game_release_date);
        // gameRating n'existe pas dans le layout actuel, laisser null
        gameRating = null;
        playButton = findViewById(R.id.play_button);
        playNativeButton = findViewById(R.id.play_native_button);
        loadSaveButton = findViewById(R.id.load_save_button);
        cheatButton = findViewById(R.id.cheat_button);
        coreOverrideButton = findViewById(R.id.core_override_button);
        viewGalleryButton = findViewById(R.id.view_gallery_button);
        pillWasm = findViewById(R.id.pill_wasm);
        pillLoad = findViewById(R.id.pill_load);
        pillNative = findViewById(R.id.pill_native);
        nativeButtonsContainer = findViewById(R.id.native_buttons_container);
        
        // Emulator Mode Toggle
        emulatorModeSwitch = findViewById(R.id.emulatorModeSwitch);
        consoleDefaultInfo = findViewById(R.id.consoleDefaultInfo);
    }
    
    /**
     * Popule les détails du jeu depuis le gamelist.json (pas de lookup runtime)
     * Style ES: toutes les métadonnées sont dans le gamelist.json
     */
    private void populateGameDetails() {
        // Titre du jeu
        gameTitle.setText(game.getName());
        gameTitle.setTypeface(null, Typeface.BOLD);
        
        // Images (load immediately)
        loadGameImages();
        
        // Afficher les métadonnées directement depuis le Game object (gamelist.json)
        updateGameDetailsFromGamelist();
    }
    
    /**
     * Update game details UI avec métadonnées du gamelist.json
     * Style ES: toutes les métadonnées sont déjà dans le gamelist.json
     */
    private void updateGameDetailsFromGamelist() {
        // Stocker hash pour GameInfoDialog
        currentGameCRC = game.getHash();
        currentGameInfo = null; // Plus de lookup runtime
        updateGameInfoButtonState();
        
        // Description
        String description = (game.getDesc() != null && !game.getDesc().isEmpty()) 
            ? game.getDesc() 
            : "No description available";
        
        // Ajouter hash si disponible
        if (game.getHash() != null) {
            description += "\n\n💾 Hash: " + game.getHash();
        }
        gameDescription.setText(description);
        
        // Genre
        String displayGenre = (game.getGenre() != null && !game.getGenre().isEmpty())
            ? "🎭 " + game.getGenre()
            : "🎭 Unknown";
        gameGenre.setText(displayGenre);
        
        // Nombre de joueurs
        String playersText = "👥 ";
        if (game.getPlayers() != null && !game.getPlayers().isEmpty()) {
            playersText += game.getPlayers();
        } else {
            playersText += "1";
        }
        gamePlayers.setText(playersText);
        
        // Date de sortie
        String releaseDate = formatReleaseDate(game.getReleasedate());
        if (game.getDeveloper() != null && !game.getDeveloper().isEmpty()) {
            releaseDate += " • 🏢 " + game.getDeveloper();
        }
        if (game.getPublisher() != null && !game.getPublisher().isEmpty() && 
            !game.getPublisher().equals(game.getDeveloper())) {
            releaseDate += " • 📦 " + game.getPublisher();
        }
        gameReleaseDate.setText(releaseDate);
        
        // Rating si disponible
        if (gameRating != null) {
            if (game.getRating() != null && !game.getRating().isEmpty()) {
                gameRating.setText("⭐ " + game.getRating());
                gameRating.setVisibility(View.VISIBLE);
            } else {
                gameRating.setVisibility(View.GONE);
            }
        }
    }
    
    /**
     * DEPRECATED: Résout le chemin de la ROM pour le calcul du CRC32
     * Plus utilisé - les métadonnées sont dans le gamelist.json
     */
    @Deprecated
    private String resolveRomPathForMetadata() {
        final String baseDir = "/storage/emulated/0/GameLibrary-Data/";
        final String consoleDir = getRealConsoleDirectory(game.getConsole());
        final String console = game.getConsole().toLowerCase();
        
        // Utiliser DIRECTEMENT game.getPath() (chemin local, pas URL HTTP)
        // Format: "./mario.zip" ou "mario.zip"
        String rawPath = game.getPath();
        if (rawPath == null || rawPath.isEmpty()) {
            Log.e(TAG, "[DB] game.getPath() is null or empty");
            return null;
        }
        
        // Nettoyer le chemin (enlever "./" si présent)
        String cleanPath = rawPath.startsWith("./") ? rawPath.substring(2) : rawPath;
        
        // Construire le chemin complet local
        String localPath = baseDir + consoleDir + "/" + cleanPath;
        Log.d(TAG, "[DB] Using local file path (not HTTP): " + localPath);
        
        // Extraire juste le nom du fichier pour le cache
        String fileName = cleanPath;
        int lastSlash = cleanPath.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = cleanPath.substring(lastSlash + 1);
        }
        
        // Normaliser le nom de console AVANT de détecter le type de fichier
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(console);
        Log.d(TAG, "[DB] Console: " + console + " -> Canonical: " + canonicalId);
        
        // Détecter si c'est une archive qui serait extraite en cache
        boolean isNativeCompressedFormat = 
            fileName.endsWith(".pbp") || fileName.endsWith(".chd") || 
            fileName.endsWith(".cso") || fileName.endsWith(".daa");
        boolean isArcadeZip = (console.startsWith("fbneo") || 
                               console.equals("arcade") || 
                               console.equals("mame") || 
                               console.equals("neogeo")) && 
                              fileName.endsWith(".zip");
        boolean isArchive = (fileName.endsWith(".zip") || fileName.endsWith(".7z")) && !isArcadeZip;
        
        // TOUTES les consoles: vérifier le cache d'abord si c'est une archive
        // (même pour les consoles non listées dans le switch, elles utiliseront .bin par défaut)
        if (isArchive && !isNativeCompressedFormat) {
            // Construire le chemin du cache (MÊME logique que extractToCacheAsync)
            // Utiliser le répertoire réel de la console (pas l'ID canonique)
            final String cacheDir = baseDir + ".cache/" + consoleDir;
            
            // Déterminer l'extension cible selon la console
            // IMPORTANT: Le default utilise .bin pour TOUTES les consoles non listées
            final String targetExtension;
            switch (canonicalId) {
                case "lynx": targetExtension = ".lnx"; break;
                case "atari2600": targetExtension = ".a26"; break;
                case "atari5200":
                case "a5200": targetExtension = ".a52"; break;
                case "atari7800":
                case "a7800": targetExtension = ".a78"; break;
                case "nes":
                case "famicom":
                case "fc": targetExtension = ".nes"; break;
                case "snes":
                case "sfc":
                case "superfamicom": targetExtension = ".sfc"; break;
                case "n64": targetExtension = ".z64"; break;
                case "gb": targetExtension = ".gb"; break;
                case "gbc": targetExtension = ".gbc"; break;
                case "gba": targetExtension = ".gba"; break;
                case "genesis":
                case "megadrive":
                case "md": targetExtension = ".bin"; break;
                case "mastersystem":
                case "sms": targetExtension = ".sms"; break;
                case "gamegear":
                case "gg": targetExtension = ".gg"; break;
                case "32x": targetExtension = ".32x"; break;
                case "ngp": targetExtension = ".ngp"; break;
                case "wonderswancolor":
                case "ws": targetExtension = ".ws"; break;
                case "pce": targetExtension = ".pce"; break;
                case "psx":
                case "ps1":
                case "playstation": targetExtension = ".bin"; break; // PSX peut être .bin, .cue, .iso, etc.
                case "psp": targetExtension = ".iso"; break;
                case "fbneo":
                case "arcade": targetExtension = ".zip"; break;
                // DEFAULT: Pour TOUTES les autres consoles non listées, utiliser .bin
                // Cela garantit que toutes les consoles fonctionnent, même celles non explicitement listées
                default: 
                    targetExtension = ".bin"; 
                    Log.d(TAG, "[DB] Console '" + canonicalId + "' not in switch, using default .bin extension");
                    break;
            }
            
            // Nom du fichier extrait (sans région) - MÊME logique que extractToCacheAsync
            String simpleName = game.getName().replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            String cachedRomPath = cacheDir + "/" + simpleName + targetExtension;
            
            // Vérifier si déjà en cache (nom exact)
            java.io.File cachedRomFile = new java.io.File(cachedRomPath);
            if (cachedRomFile.exists()) {
                Log.d(TAG, "[DB] Using cached ROM for CRC calculation: " + cachedRomPath);
                return cachedRomPath;
            }
            
            // Si pas trouvé, chercher dans le répertoire cache (variantes de noms)
            java.io.File cacheDirFile = new java.io.File(cacheDir);
            if (cacheDirFile.exists() && cacheDirFile.isDirectory()) {
                java.io.File[] cacheFiles = cacheDirFile.listFiles();
                if (cacheFiles != null) {
                    Log.d(TAG, "[DB] Cache directory exists, searching for: " + simpleName + targetExtension + " (found " + cacheFiles.length + " files)");
                    // Chercher un fichier avec la bonne extension
                    for (java.io.File file : cacheFiles) {
                        if (file.isFile() && file.getName().endsWith(targetExtension)) {
                            // Vérifier si le nom correspond (sans tenir compte de la casse et des caractères spéciaux)
                            String cacheFileName = file.getName().replace(targetExtension, "").toLowerCase();
                            String simpleNameLower = simpleName.toLowerCase();
                            if (cacheFileName.equals(simpleNameLower) || 
                                cacheFileName.contains(simpleNameLower) || 
                                simpleNameLower.contains(cacheFileName)) {
                                Log.d(TAG, "[DB] Using cached ROM (variant name) for CRC calculation: " + file.getAbsolutePath());
                                return file.getAbsolutePath();
                            }
                        }
                    }
                    Log.d(TAG, "[DB] No matching cached ROM found in cache directory");
                }
            } else {
                Log.d(TAG, "[DB] Cache directory does not exist: " + cacheDir);
            }
        }
        
        // Sinon, utiliser le chemin local direct (pas d'URL HTTP)
        // DatabaseManager.calculateCRC32() gère l'extraction depuis ZIP automatiquement
        java.io.File localFile = new java.io.File(localPath);
        if (localFile.exists()) {
            Log.d(TAG, "[DB] Using local file for CRC calculation: " + localPath);
            return localPath;
        } else {
            Log.w(TAG, "[DB] Local file does not exist: " + localPath);
            // Dernier recours: essayer avec juste le nom du fichier
            String fallbackPath = baseDir + consoleDir + "/" + fileName;
            java.io.File fallbackFile = new java.io.File(fallbackPath);
            if (fallbackFile.exists()) {
                Log.d(TAG, "[DB] Using fallback path: " + fallbackPath);
                return fallbackPath;
            }
            Log.e(TAG, "[DB] Neither localPath nor fallbackPath exist");
            return localPath; // Retourner quand même pour que calculateCRC32() gère l'erreur
        }
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
        
        viewGalleryButton.setOnClickListener(v -> openScreenshotGallery());
        
        // Afficher les boutons natifs pour TOUTES les consoles
        // L'utilisateur peut maintenant choisir entre WASM et NATIVE pour n'importe quelle console
        String console = game.getConsole().toLowerCase();
        nativeButtonsContainer.setVisibility(View.VISIBLE);
        
        // Vérifier si des sauvegardes existent dans les slots
        checkAndShowLoadSaveButton();
        
        // Dual/Triple-Color Pill - WASM (Red), LOAD (Blue), and Native (Green)
        pillWasm.setOnClickListener(v -> {
            Log.i(TAG, "WASM pill clicked - launching EmulatorJS");
            launchGame();
        });
        
        pillLoad.setOnClickListener(v -> {
            Log.i(TAG, "LOAD pill clicked - opening slot selection");
            showSlotSelectionDialog();
        });
        
        pillNative.setOnClickListener(v -> {
            Log.i(TAG, "NATIVE pill clicked - launching RetroArch");
            launchGameNative(0);  // 0 = new game
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Rafraîchir le bouton de core override au cas où il aurait été changé
        // depuis NativeComposeEmulatorActivity (via le dialog d'erreur)
        updateCoreOverrideButton();
        if (favoriteButton != null) {
            updateFavoriteButton();
        }
        syncGameInfoDialogState();
        
        // Réappliquer le thème au cas où il aurait changé
        applyTheme();
    }
    
    private void launchGame() {
        Log.i(TAG, "Lancement du jeu (WASM): " + game.getName());
        
        // Get console configuration (options avancées non accessibles dans EmulatorJS GUI)
        ConsoleConfigActivity.ConsoleConfig config = ConsoleConfigActivity.getConfig(this, game.getConsole());
        
        // Get core override if exists
        // Extraire le nom de fichier depuis le chemin local
        String fileName = game.getPath();
        if (fileName.startsWith("./")) {
            fileName = fileName.substring(2);
        }
        // Extraire juste le nom du fichier (sans le chemin)
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
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
            // Utiliser getFileUrl() pour le WebView qui a besoin d'une URL HTTP
            intent.putExtra("file", game.getFileUrl());
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
     * Count number of cheats in a .cht file
     */
    private int countCheatsInFile(java.io.File cheatFile) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cheatFile));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("cheats =")) {
                    reader.close();
                    return Integer.parseInt(line.split("=")[1].trim());
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Error counting cheats: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Mappe le nom de console au vrai nom de repertoire sur le device
     * Resout les problemes de duplication (lynx/atarilynx, sms/mastersystem, etc.)
     */
    private String getRealConsoleDirectory(String consoleName) {
        // Retourner le nom original du répertoire
        // L'ID canonique est utilisé pour la configuration (cores, extensions, etc.)
        // mais le nom du répertoire reste celui de l'utilisateur
        return consoleName.toLowerCase();
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
        
        // Extraire le nom du fichier depuis le chemin local
        String fileName = game.getPath();
        if (fileName.startsWith("./")) {
            fileName = fileName.substring(2);
        }
        // Extraire juste le nom du fichier (sans le chemin)
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
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
        
        // === DATABASE LOOKUP (NEW) ===
        // Calculate CRC32 and lookup game metadata
        String gameCRC = com.retroplay.database.DatabaseManager.INSTANCE.calculateCRC32(romPath);
        
        // Fallback: Si le calcul échoue, utiliser le hash du gamelist.json si c'est un CRC32 (8 caractères hex)
        if (gameCRC == null && game.getHash() != null && !game.getHash().isEmpty()) {
            String hash = game.getHash();
            // CRC32 = 8 caractères hexadécimaux (ex: "A1B2C3D4")
            if (hash.length() == 8 && hash.matches("[0-9A-Fa-f]{8}")) {
                gameCRC = hash.toUpperCase();
                Log.i(TAG, "Using CRC32 from gamelist.json: " + gameCRC);
            }
        }
        
        if (gameCRC != null) {
            Log.i(TAG, "ROM CRC32: " + gameCRC);
            
            com.retroplay.database.GameInfo gameInfo = com.retroplay.database.DatabaseManager.INSTANCE.lookupGame(gameCRC, game.getConsole());
            if (gameInfo != null) {
                Log.i(TAG, "✅ Game identified from database:");
                Log.i(TAG, "  Name: " + gameInfo.getName());
                Log.i(TAG, "  Genre: " + gameInfo.getGenre());
                Log.i(TAG, "  Developer: " + gameInfo.getDeveloper());
                Log.i(TAG, "  Year: " + gameInfo.getReleaseYear());
                Log.i(TAG, "  " + gameInfo.getDisplayInfo());
                
                // Check cheats available
                java.io.File cheatFile = com.retroplay.database.DatabaseManager.INSTANCE.getCheatsPath(gameInfo, game.getConsole());
                if (cheatFile != null && cheatFile.exists()) {
                    int cheatCount = countCheatsInFile(cheatFile);
                    Log.i(TAG, "  🎮 " + cheatCount + " cheats available!");
                    
                    // Show notification
                    Toast.makeText(this, 
                        cheatCount + " cheats available • " + gameInfo.getDisplayInfo(), 
                        Toast.LENGTH_LONG).show();
                }
            } else {
                Log.w(TAG, "⚠️ Game not found in database (CRC: " + gameCRC + ")");
            }
        } else {
            Log.w(TAG, "⚠️ Could not determine CRC32 for game (ROM calculation failed and no CRC32 in gamelist.json)");
        }
        
        // Determine which emulator activity to use based on user preference
        Class<?> emulatorActivity = getEmulatorActivityClass(game.getConsole());
        
        Intent intent = new Intent(this, emulatorActivity);
        intent.putExtra("romPath", romPath);
        intent.putExtra("gameName", game.getName());
        intent.putExtra("gameId", currentGalleryGameId);
        intent.putExtra("console", game.getConsole());
        intent.putExtra("loadSlot", slot);  // 0 = nouvelle partie, 1-5 = charger slot
        
        // Pass database metadata if available
        if (gameCRC != null) {
            intent.putExtra("gameCRC", gameCRC);
        }
        
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
        
        // Normaliser le nom de console avec ConsoleNameMapper
        final String canonicalId = ConsoleNameMapper.normalizeToCanonical(console);
        
        // Determiner l'extension cible selon la console (utilise ID canonique)
        final String targetExtension;
        switch (canonicalId) {
            // Atari
            case "lynx":
                targetExtension = ".lnx";
                break;
            case "atari2600":
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
                targetExtension = ".bin";  // Ou .smd, .md, .gen
                break;
            case "mastersystem":
                targetExtension = ".sms";
                break;
            case "gamegear":
                targetExtension = ".gg";
                break;
            case "32x":
                targetExtension = ".32x";
                break;
            
            // Other
            case "ngp":
                targetExtension = ".ngp";
                break;
            case "wonderswancolor":
                targetExtension = ".ws";
                break;
            case "pce":
                targetExtension = ".pce";
                break;
            
            // Arcade (FBNeo) - garde .zip (ROM sets)
            case "fbneo":
            case "arcade":
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
                        
                        if (isValidRomFormat(entryName, canonicalId)) {
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
                        
                        if (isValidRomFormat(entryName, canonicalId)) {
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
     * Utilise ConsoleNameMapper pour normaliser les noms alternatifs
     */
    private boolean isValidRomFormat(String entryName, String console) {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(console);
        
        // Utiliser ID canonique pour la vérification
        switch (canonicalId) {
            // Lynx
            case "lynx":
                return entryName.endsWith(".lnx");
            // Atari 2600
            case "atari2600":
                return entryName.endsWith(".a26") || entryName.endsWith(".bin");
            // Atari 5200
            case "atari5200":
                return entryName.endsWith(".a52") || entryName.endsWith(".bin");
            // Atari 7800
            case "atari7800":
                return entryName.endsWith(".a78") || entryName.endsWith(".bin");
            // Note: .zip et .7z sont gérés séparément dans extractToCacheAsync()
            // NES
            case "nes":
                return entryName.endsWith(".nes") || entryName.endsWith(".fds") || entryName.endsWith(".unf");
            // SNES
            case "snes":
                return entryName.endsWith(".sfc") || entryName.endsWith(".smc");
            // N64
            case "n64":
                return entryName.endsWith(".z64") || entryName.endsWith(".n64") || entryName.endsWith(".v64");
            // GB
            case "gb":
                return entryName.endsWith(".gb") || entryName.endsWith(".sgb");
            // GBC
            case "gbc":
                return entryName.endsWith(".gbc") || entryName.endsWith(".gb");
            // GBA
            case "gba":
                return entryName.endsWith(".gba") || entryName.endsWith(".agb");
            // Genesis / MegaDrive
            case "genesis":
                return entryName.endsWith(".bin") || entryName.endsWith(".smd") || 
                      entryName.endsWith(".md") || entryName.endsWith(".gen");
            // Master System
            case "mastersystem":
                return entryName.endsWith(".sms") || entryName.endsWith(".bin");
            // Game Gear
            case "gamegear":
                return entryName.endsWith(".gg") || entryName.endsWith(".bin");
            // 32X
            case "32x":
                return entryName.endsWith(".32x") || entryName.endsWith(".bin");
            // Neo Geo Pocket
            case "ngp":
                return entryName.endsWith(".ngp") || entryName.endsWith(".ngc");
            // WonderSwan
            case "wonderswancolor":
                return entryName.endsWith(".ws") || entryName.endsWith(".wsc");
            // PC Engine
            case "pce":
                return entryName.endsWith(".pce") || entryName.endsWith(".sgx");
            // Arcade (FBNeo) - ROMs en .zip (ROM sets)
            case "fbneo":
            case "arcade":
                return entryName.endsWith(".zip");  // Les ROMs arcade sont en .zip
            // Fallback générique
            default:
                return entryName.endsWith(".bin") || entryName.endsWith(".rom");
        }
    }
    
    /**
     * Lance l'emulateur avec une ROM deja extraite ou en cache
     */
    private void launchWithCachedRom(String romPath, int slot) {
        // === DATABASE LOOKUP (NEW) ===
        // Calculate CRC32 and lookup game metadata
        String gameCRC = com.retroplay.database.DatabaseManager.INSTANCE.calculateCRC32(romPath);
        
        // Fallback: Si le calcul échoue, utiliser le hash du gamelist.json si c'est un CRC32 (8 caractères hex)
        if (gameCRC == null && game.getHash() != null && !game.getHash().isEmpty()) {
            String hash = game.getHash();
            // CRC32 = 8 caractères hexadécimaux (ex: "A1B2C3D4")
            if (hash.length() == 8 && hash.matches("[0-9A-Fa-f]{8}")) {
                gameCRC = hash.toUpperCase();
                Log.i(TAG, "Using CRC32 from gamelist.json: " + gameCRC);
            }
        }
        
        if (gameCRC != null) {
            Log.i(TAG, "ROM CRC32: " + gameCRC);
            
            com.retroplay.database.GameInfo gameInfo = com.retroplay.database.DatabaseManager.INSTANCE.lookupGame(gameCRC, game.getConsole());
            if (gameInfo != null) {
                Log.i(TAG, "✅ Game identified from database:");
                Log.i(TAG, "  Name: " + gameInfo.getName());
                Log.i(TAG, "  Genre: " + gameInfo.getGenre());
                Log.i(TAG, "  Developer: " + gameInfo.getDeveloper());
                Log.i(TAG, "  Year: " + gameInfo.getReleaseYear());
                Log.i(TAG, "  " + gameInfo.getDisplayInfo());
                
                // Check cheats available
                java.io.File cheatFile = com.retroplay.database.DatabaseManager.INSTANCE.getCheatsPath(gameInfo, game.getConsole());
                if (cheatFile != null && cheatFile.exists()) {
                    int cheatCount = countCheatsInFile(cheatFile);
                    Log.i(TAG, "  🎮 " + cheatCount + " cheats available!");
                    
                    // Show notification
                    Toast.makeText(this, 
                        cheatCount + " cheats available • " + gameInfo.getDisplayInfo(), 
                        Toast.LENGTH_LONG).show();
                }
            } else {
                Log.w(TAG, "⚠️ Game not found in database (CRC: " + gameCRC + ")");
            }
        } else {
            Log.w(TAG, "⚠️ Could not determine CRC32 for game (ROM calculation failed and no CRC32 in gamelist.json)");
        }
        
        // Determine which emulator activity to use based on user preference
        Class<?> emulatorActivity = getEmulatorActivityClass(game.getConsole());
        
        Intent intent = new Intent(this, emulatorActivity);
        intent.putExtra("romPath", romPath);
        intent.putExtra("gameName", game.getName());
        intent.putExtra("gameId", currentGalleryGameId);
        intent.putExtra("console", game.getConsole());
        intent.putExtra("loadSlot", slot);
        
        // Pass database metadata if available
        if (gameCRC != null) {
            intent.putExtra("gameCRC", gameCRC);
        }
        
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
        
        // Obtenir les couleurs du thème
        ThemeManager themeManager = ThemeManager.getInstance(this);
        int primaryColor = themeManager.getPrimaryColor(this);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);
        int textSecondaryColor = themeManager.getTextSecondaryColor(this);
        
        // Créer un ListView personnalisé avec thumbnails
        ListView listView = new ListView(this);
        listView.setBackgroundColor(headerBackgroundColor);
        
        // Créer un adapter personnalisé pour afficher les slots avec thumbnails
        ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_list_item_1, new Integer[]{1, 2, 3, 4, 5}) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                int slot = getItem(position);
                
                // Créer une vue personnalisée avec thumbnail
                LinearLayout itemView = new LinearLayout(GameDetailsActivity.this);
                itemView.setOrientation(LinearLayout.HORIZONTAL);
                itemView.setPadding(16, 16, 16, 16);
                itemView.setBackgroundColor(headerBackgroundColor);
                
                // ImageView pour le thumbnail
                ImageView thumbnailView = new ImageView(GameDetailsActivity.this);
                int thumbnailSize = (int) (80 * getResources().getDisplayMetrics().density); // 80dp
                LinearLayout.LayoutParams thumbnailParams = new LinearLayout.LayoutParams(thumbnailSize, thumbnailSize);
                thumbnailParams.setMargins(0, 0, 16, 0);
                thumbnailView.setLayoutParams(thumbnailParams);
                thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                
                // Créer un drawable pour le contour du thumbnail
                android.graphics.drawable.GradientDrawable thumbnailBorder = new android.graphics.drawable.GradientDrawable();
                thumbnailBorder.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                thumbnailBorder.setCornerRadius(6 * getResources().getDisplayMetrics().density);
                thumbnailBorder.setStroke((int)(1 * getResources().getDisplayMetrics().density), primaryColor);
                
                // TextView pour les infos
                TextView textView = new TextView(GameDetailsActivity.this);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                textView.setLayoutParams(textParams);
                
                // Vérifier si le slot existe
                java.io.File saveFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + slot + "/" + gameName + ".state");
                java.io.File thumbnailFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + slot + "/thumbnail.png");
                
                if (saveFile.exists()) {
                    // Slot occupé
                    long lastModified = saveFile.lastModified();
                    long sizeKB = saveFile.length() / 1024;
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                    String dateStr = dateFormat.format(new java.util.Date(lastModified));
                    
                    textView.setText("Slot " + slot + "\n" + dateStr + " (" + sizeKB + "KB)");
                    textView.setTextColor(primaryColor);
                    textView.setAlpha(1.0f);
                    
                    // Charger le thumbnail si disponible
                    if (thumbnailFile.exists()) {
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(thumbnailFile.getAbsolutePath());
                        if (bitmap != null) {
                            thumbnailView.setImageBitmap(bitmap);
                            thumbnailView.setBackground(thumbnailBorder);
                        } else {
                            // Placeholder si le bitmap ne peut pas être chargé
                            thumbnailView.setImageDrawable(null);
                            thumbnailBorder.setColor(headerBackgroundColor);
                            thumbnailView.setBackground(thumbnailBorder);
                        }
                    } else {
                        // Placeholder si pas de thumbnail
                        thumbnailView.setImageDrawable(null);
                        thumbnailBorder.setColor(headerBackgroundColor);
                        thumbnailView.setBackground(thumbnailBorder);
                    }
                } else {
                    // Slot vide
                    textView.setText("Slot " + slot + " [Empty]");
                    textView.setTextColor(textSecondaryColor);
                    textView.setAlpha(1.0f);
                    thumbnailView.setImageDrawable(null);
                    thumbnailBorder.setColor(headerBackgroundColor);
                    thumbnailView.setBackground(thumbnailBorder);
                }
                
                itemView.addView(thumbnailView);
                itemView.addView(textView);
                
                return itemView;
            }
        };
        
        listView.setAdapter(adapter);
        
        // Afficher le dialogue de sélection
        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Load Game - " + console.toUpperCase())
                .setView(listView)
                .setNegativeButton("Cancel", null)
                .create();
        
        // Appliquer le thème au dialog
        dialog.setOnShowListener(dialogInterface -> {
            // Fond du dialog
            android.view.View dialogView = dialog.getWindow().getDecorView();
            if (dialogView != null) {
                dialogView.setBackgroundColor(headerBackgroundColor);
            }
            
            // Header (titre) - plusieurs méthodes pour trouver le titre
            TextView titleView = null;
            
            // Méthode 1: Par ID Android
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            if (titleId != 0) {
                titleView = dialog.findViewById(titleId);
            }
            
            // Méthode 2: Par ID AppCompat
            if (titleView == null) {
                titleId = getResources().getIdentifier("alertTitle", "id", getPackageName());
                if (titleId != 0) {
                    titleView = dialog.findViewById(titleId);
                }
            }
            
            // Méthode 3: Chercher dans la hiérarchie
            if (titleView == null) {
                android.view.ViewGroup parent = (android.view.ViewGroup) dialog.getWindow().getDecorView();
                titleView = findTextViewByText(parent, "Load Game");
            }
            
            if (titleView != null) {
                titleView.setTextColor(primaryColor);
                titleView.setAlpha(1.0f);
            }
            
            // Footer (bouton Cancel)
            android.widget.Button cancelButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (cancelButton != null) {
                cancelButton.setTextColor(primaryColor);
                cancelButton.setAlpha(1.0f);
                // Fond du bouton
                cancelButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            }
            
            // Appliquer le fond au header si possible
            android.view.ViewGroup parent = (android.view.ViewGroup) dialog.getWindow().getDecorView();
            applyThemeToDialogViews(parent, primaryColor, headerBackgroundColor);
        });
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            int selectedSlot = position + 1;
            
            // Vérifier si le slot existe
            java.io.File saveFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + selectedSlot + "/" + gameName + ".state");
            if (saveFile.exists()) {
                dialog.dismiss();
                launchGameNative(selectedSlot);
            } else {
                Toast.makeText(this, "No save in Slot " + selectedSlot, Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }
    
    /**
     * Helper pour trouver un TextView par son texte
     */
    private TextView findTextViewByText(android.view.ViewGroup parent, String text) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if (tv.getText().toString().contains(text)) {
                    return tv;
                }
            } else if (child instanceof android.view.ViewGroup) {
                TextView found = findTextViewByText((android.view.ViewGroup) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * Appliquer le thème aux vues du dialog
     */
    private void applyThemeToDialogViews(android.view.ViewGroup parent, int primaryColor, int headerBackgroundColor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);
            
            // Appliquer le fond aux LinearLayout/FrameLayout qui sont probablement le header/footer
            if (child instanceof LinearLayout || child instanceof FrameLayout) {
                if (child.getBackground() == null || (child.getBackground() instanceof android.graphics.drawable.ColorDrawable && 
                    ((android.graphics.drawable.ColorDrawable) child.getBackground()).getAlpha() < 255)) {
                    child.setBackgroundColor(headerBackgroundColor);
                }
            }
            
            // Appliquer la couleur aux TextViews
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                // Si c'est un titre (gros texte)
                if (tv.getTextSize() > 18) {
                    tv.setTextColor(primaryColor);
                    tv.setAlpha(1.0f);
                }
            }
            
            // Récursion pour les ViewGroups
            if (child instanceof android.view.ViewGroup) {
                applyThemeToDialogViews((android.view.ViewGroup) child, primaryColor, headerBackgroundColor);
            }
        }
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
            // Afficher le bouton pill LOAD dans le dual pill container
            pillLoad.setVisibility(View.VISIBLE);
            // Afficher aussi le bouton normal dans la section native (pour compatibilité)
            loadSaveButton.setVisibility(View.VISIBLE);
            Log.i(TAG, "Save slots found for: " + gameName);
        } else {
            // Masquer les deux boutons de chargement
            pillLoad.setVisibility(View.GONE);
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
        if (favoritesManager == null) {
            return;
        }
        
        // Toggle dans le manager et mettre à jour l'état du jeu
        boolean isFavorite = favoritesManager.toggleFavorite(game);
        game.setFavorite(isFavorite);
        
        // Mettre à jour l'UI
        updateFavoriteButton();
        
        // Afficher un message de confirmation
        String message = isFavorite ? "Added to favorites" : "Removed from favorites";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        Log.i(TAG, "Toggle favorite for: " + game.getName() + " - isFavorite: " + isFavorite);
    }

    private void openScreenshotGallery() {
        Intent intent = new Intent(this, com.retroplay.gallery.ScreenshotGalleryActivity.class);
        intent.putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_CONSOLE, currentConsole);
        intent.putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_GAME_ID, currentGalleryGameId);
        intent.putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_GAME_NAME, game.getName());
        startActivity(intent);
    }
    
    private void applyTheme() {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        int primaryColor = themeManager.getPrimaryColor(this);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);
        int textPrimaryColor = themeManager.getTextPrimaryColor(this);
        int textSecondaryColor = themeManager.getTextSecondaryColor(this);
        
        // Header buttons
        if (headerBackButton != null) {
            headerBackButton.setIconTint(ColorStateList.valueOf(primaryColor));
            headerBackButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            headerBackButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            headerBackButton.setAlpha(1.0f);
        }
        
        if (headerSettingsButton != null) {
            headerSettingsButton.setIconTint(ColorStateList.valueOf(primaryColor));
            headerSettingsButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            headerSettingsButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            headerSettingsButton.setAlpha(1.0f);
        }
        
        // Header title
        if (headerTitle != null) {
            headerTitle.setTextColor(primaryColor);
            headerTitle.setAlpha(1.0f);
        }
        
        // Game title
        if (gameTitle != null) {
            gameTitle.setTextColor(primaryColor);
            gameTitle.setAlpha(1.0f);
        }
        
        // Game info texts
        if (gameDescription != null) {
            gameDescription.setTextColor(textSecondaryColor);
            gameDescription.setAlpha(1.0f);
        }
        if (gameGenre != null) {
            gameGenre.setTextColor(textSecondaryColor);
            gameGenre.setAlpha(1.0f);
        }
        if (gamePlayers != null) {
            gamePlayers.setTextColor(textSecondaryColor);
            gamePlayers.setAlpha(1.0f);
        }
        if (gameReleaseDate != null) {
            gameReleaseDate.setTextColor(textSecondaryColor);
            gameReleaseDate.setAlpha(1.0f);
        }
        
        // Action buttons
        if (playNativeButton != null) {
            playNativeButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            playNativeButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.kitt_black)));
            playNativeButton.setAlpha(1.0f);
        }
        
        if (loadSaveButton != null) {
            loadSaveButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            loadSaveButton.setIconTint(ColorStateList.valueOf(primaryColor));
            loadSaveButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            loadSaveButton.setAlpha(1.0f);
        }
        
        if (cheatButton != null) {
            cheatButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            cheatButton.setIconTint(ColorStateList.valueOf(primaryColor));
            cheatButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            cheatButton.setAlpha(1.0f);
        }
        
        if (coreOverrideButton != null) {
            coreOverrideButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            coreOverrideButton.setTextColor(primaryColor);
            coreOverrideButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            coreOverrideButton.setAlpha(1.0f);
        }
        
        if (viewGalleryButton != null) {
            viewGalleryButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            viewGalleryButton.setIconTint(ColorStateList.valueOf(primaryColor));
            viewGalleryButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            viewGalleryButton.setAlpha(1.0f);
        }
        
        // Play button (Button, not MaterialButton)
        if (playButton != null) {
            playButton.setTextColor(primaryColor);
            playButton.setAlpha(1.0f);
            // Note: Button background is typically set via drawable, but we can set text color
        }
        
        // Pills (FrameLayout) - these might have backgrounds set in XML, but we can ensure text colors
        // Note: Pills are typically styled via their child TextViews
        
        // Console default info
        if (consoleDefaultInfo != null) {
            consoleDefaultInfo.setTextColor(textSecondaryColor);
            consoleDefaultInfo.setAlpha(1.0f);
        }
        
        // Favorite and Game Info buttons are handled in their update methods
        updateFavoriteButton();
        updateGameInfoButtonState();
    }
    
    private void updateFavoriteButton() {
        if (favoriteButton == null || favoritesManager == null) {
            return;
        }
        
        ThemeManager themeManager = ThemeManager.getInstance(this);
        ColorStateList accent = ColorStateList.valueOf(themeManager.getPrimaryColor(this));
        ColorStateList headerBackground = ColorStateList.valueOf(themeManager.getHeaderBackgroundColor(this));
        favoriteButton.setStrokeColor(accent);
        favoriteButton.setBackgroundTintList(headerBackground);
        if (favoritesManager.isFavorite(game)) {
            favoriteButton.setIconResource(R.drawable.ic_favorite_24);
            favoriteButton.setIconTint(accent);
            game.setFavorite(true);
        } else {
            favoriteButton.setIconResource(R.drawable.ic_favorite_border_24);
            favoriteButton.setIconTint(accent);
            game.setFavorite(false);
        }
    }
    
    /**
     * Met à jour le texte du bouton core override selon l'état actuel
     */
    private void updateCoreOverrideButton() {
        // Extraire le nom de fichier depuis le chemin local
        String fileName = game.getPath();
        if (fileName.startsWith("./")) {
            fileName = fileName.substring(2);
        }
        // Extraire juste le nom du fichier (sans le chemin)
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
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
            case "wonderswancolor":
            case "ws":
            case "wsc": return "Mednafen WonderSwan";
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
        // Extraire le nom de fichier depuis le chemin local
        String fileName = game.getPath();
        if (fileName.startsWith("./")) {
            fileName = fileName.substring(2);
        }
        // Extraire juste le nom du fichier (sans le chemin)
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        
        String consoleDir = getRealConsoleDirectory(game.getConsole());
        String relativePath = consoleDir + "/" + fileName;
        
        // Obtenir le core par défaut
        String defaultCoreName = getDefaultCoreForConsole(game.getConsole());
        
        // Charger les cores disponibles depuis cores.json
        java.util.List<CoreInfo> availableCores = loadAvailableCoresForConsole(game.getConsole());
        
        // Construire les listes pour le dialog
        java.util.List<String> coresList = new java.util.ArrayList<>();
        java.util.List<String> coreIdsList = new java.util.ArrayList<>();
        
        // Ajouter "Default" en premier
        coresList.add("Default (" + defaultCoreName + ")");
        coreIdsList.add(null);
        
        // Organiser les cores par catégorie
        java.util.List<CoreInfo> arcadeCores = new java.util.ArrayList<>();
        java.util.List<CoreInfo> consoleCores = new java.util.ArrayList<>();
        
        for (CoreInfo core : availableCores) {
            String coreId = core.coreId.toLowerCase();
            // Détecter les cores arcade
            if (coreId.contains("mame") || coreId.contains("fbneo") || coreId.contains("fbalpha") || 
                coreId.contains("flycast") || coreId.contains("arcade")) {
                arcadeCores.add(core);
            } else {
                consoleCores.add(core);
            }
        }
        
        // Ajouter les cores arcade
        if (!arcadeCores.isEmpty()) {
            coresList.add("── ARCADE CORES ──");
            coreIdsList.add(null);
            for (CoreInfo core : arcadeCores) {
                coresList.add(core.displayName + (core.description.isEmpty() ? "" : " (" + core.description + ")"));
                coreIdsList.add(core.coreId);
            }
        }
        
        // Ajouter les cores console
        if (!consoleCores.isEmpty()) {
            coresList.add("── CONSOLE CORES ──");
            coreIdsList.add(null);
            for (CoreInfo core : consoleCores) {
                coresList.add(core.displayName + (core.description.isEmpty() ? "" : " (" + core.description + ")"));
                coreIdsList.add(core.coreId);
            }
        }
        
        // Si aucun core trouvé, utiliser la liste par défaut
        if (availableCores.isEmpty()) {
            Log.w(TAG, "No cores found for console: " + game.getConsole() + ", using fallback list");
            coresList.add("── CONSOLE CORES ──");
            coreIdsList.add(null);
            coresList.add("FCEUmm (NES)");
            coreIdsList.add("fceumm");
            coresList.add("Snes9x (SNES)");
            coreIdsList.add("snes9x");
            coresList.add("Gambatte (GB/GBC)");
            coreIdsList.add("gambatte");
            coresList.add("mGBA (GBA)");
            coreIdsList.add("mgba");
        }
        
        // Convertir en tableaux
        String[] cores = coresList.toArray(new String[0]);
        String[] coreIds = coreIdsList.toArray(new String[0]);
        
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
                // Désactiver les headers (ceux qui ont null dans coreIds, sauf position 0 qui est "Default")
                if (position == 0) return true; // "Default" est toujours activé
                if (position >= coreIds.length) return false;
                return coreIds[position] != null; // Headers ont null, donc désactivés
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
                } else if (position < coreIds.length && coreIds[position] == null && position != 0) {
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

        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Core for " + game.getName())
                .setView(listView)
                .setNegativeButton("Cancel", null)
                .create();

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            // Vérifier si c'est un header (ceux qui ont null dans coreIds)
            if (position < coreIds.length && coreIds[position] == null && position != 0) {
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
    
    /**
     * Classe interne pour représenter les informations d'un core
     */
    private static class CoreInfo {
        String coreId;
        String displayName;
        String fileName;
        String description;
        
        CoreInfo(String coreId, String displayName, String fileName, String description) {
            this.coreId = coreId;
            this.displayName = displayName;
            this.fileName = fileName;
            this.description = description;
        }
    }
    
    /**
     * Charge les cores disponibles pour une console depuis cores.json
     */
    private java.util.List<CoreInfo> loadAvailableCoresForConsole(String console) {
        java.util.List<CoreInfo> cores = new java.util.ArrayList<>();
        
        try {
            // Normaliser le nom de la console
            String consoleKey = console.toLowerCase().replace("_", "").replace("-", "");
            
            // Obtenir les patterns de cores pour cette console
            java.util.List<String> patterns = getCorePatternsForConsole(consoleKey);
            
            // Lire cores.json
            java.io.File coresFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/data/cores/cores.json");
            if (!coresFile.exists()) {
                Log.w(TAG, "cores.json not found, using fallback cores");
                return getFallbackCoresForConsole(consoleKey);
            }
            
            java.io.FileInputStream fis = new java.io.FileInputStream(coresFile);
            byte[] buffer = new byte[(int) coresFile.length()];
            fis.read(buffer);
            fis.close();
            String jsonContent = new String(buffer, "UTF-8");
            
            // Parser le JSON
            org.json.JSONArray coresArray = new org.json.JSONArray(jsonContent);
            
            // Chercher les cores compatibles
            Log.d(TAG, "Searching for cores matching patterns: " + patterns.toString() + " for console: " + console);
            for (int i = 0; i < coresArray.length(); i++) {
                org.json.JSONObject core = coresArray.getJSONObject(i);
                String coreName = core.optString("name", "").toLowerCase();
                String coreDisplayName = core.optString("display_name", core.optString("name", ""));
                String coreId = core.optString("id", coreName).toLowerCase();
                String coreFileName = core.optString("file", "");
                
                // Normaliser les noms pour la comparaison (enlever underscores et tirets)
                String coreNameNormalized = coreName.replace("_", "").replace("-", "");
                String coreIdNormalized = coreId.replace("_", "").replace("-", "");
                
                // Vérifier si le core correspond à un pattern
                boolean matched = false;
                for (String pattern : patterns) {
                    String patternLower = pattern.toLowerCase();
                    String patternNormalized = patternLower.replace("_", "").replace("-", "");
                    
                    // Vérifier dans le nom, l'ID, et les versions normalisées
                    if (coreName.contains(patternLower) || coreId.contains(patternLower) ||
                        coreNameNormalized.contains(patternNormalized) || coreIdNormalized.contains(patternNormalized)) {
                        cores.add(new CoreInfo(coreId, coreDisplayName, coreFileName, ""));
                        Log.d(TAG, "Matched core: " + coreDisplayName + " (id: " + coreId + ", pattern: " + pattern + ")");
                        matched = true;
                        break;
                    }
                }
                
                if (!matched) {
                    Log.v(TAG, "Core not matched: " + coreDisplayName + " (name: " + coreName + ", id: " + coreId + ")");
                }
            }
            
            Log.i(TAG, "Loaded " + cores.size() + " compatible cores for console: " + console);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading cores from cores.json", e);
            return getFallbackCoresForConsole(console.toLowerCase().replace("_", "").replace("-", ""));
        }
        
        return cores;
    }
    
    /**
     * Retourne les patterns de noms de cores à chercher pour une console
     */
    private java.util.List<String> getCorePatternsForConsole(String consoleKey) {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleKey);
        
        java.util.List<String> patterns = new java.util.ArrayList<>();
        
        switch (canonicalId) {
            case "nes":
                patterns.add("fceumm");
                patterns.add("fceux");
                patterns.add("mesen");
                patterns.add("nestopia");
                break;
            case "snes":
                patterns.add("snes9x");
                patterns.add("bsnes");
                patterns.add("higan");
                break;
            case "n64":
                patterns.add("parallel");
                patterns.add("mupen64");
                patterns.add("n64");
                break;
            case "gb":
            case "gbc":
                patterns.add("gambatte");
                patterns.add("sameboy");
                patterns.add("gameboy");
                break;
            case "gba":
                patterns.add("mgba");
                patterns.add("vba");
                patterns.add("gba");
                break;
            case "psx":
            case "ps1":
            case "playstation":
                patterns.add("pcsx");
                patterns.add("mednafen_psx");
                patterns.add("beetle_psx");
                break;
            case "psp":
                patterns.add("ppsspp");
                patterns.add("psp");
                break;
            case "genesis":
            case "megadrive":
            case "md":
                patterns.add("genesis");
                patterns.add("picodrive");
                break;
            case "wonderswan":
            case "wonderswancolor":
            case "ws":
            case "wsc":
                patterns.add("mednafen_wswan");
                patterns.add("wonderswan");
                break;
            case "ngp":
            case "ngpc":
                patterns.add("mednafen_ngp");
                patterns.add("ngp");
                break;
            case "pce":
            case "pcengine":
                patterns.add("mednafen_pce");
                patterns.add("pcengine");
                break;
            case "arcade":
            case "mame":
                patterns.add("mame");
                patterns.add("fbneo");
                break;
            default:
                // Pour les consoles non reconnues, essayer de trouver un pattern basé sur le nom
                patterns.add(consoleKey);
                break;
        }
        
        return patterns;
    }
    
    /**
     * Retourne une liste de cores par défaut si cores.json n'est pas disponible
     */
    private java.util.List<CoreInfo> getFallbackCoresForConsole(String consoleKey) {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleKey);
        
        java.util.List<CoreInfo> cores = new java.util.ArrayList<>();
        
        switch (canonicalId) {
            case "wonderswancolor":
                cores.add(new CoreInfo("mednafen_wswan", "Mednafen WonderSwan", "mednafen_wswan_libretro_android.so", ""));
                break;
            default:
                // Liste par défaut
                cores.add(new CoreInfo("fceumm", "FCEUmm", "fceumm_libretro_android.so", "NES"));
                cores.add(new CoreInfo("snes9x", "Snes9x", "snes9x_libretro_android.so", "SNES"));
                cores.add(new CoreInfo("gambatte", "Gambatte", "gambatte_libretro_android.so", "GB/GBC"));
                cores.add(new CoreInfo("mgba", "mGBA", "libmgba_libretro_android.so", "GBA"));
                break;
        }
        
        return cores;
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
    
    /**
     * Get the appropriate emulator activity class based on user preference
     * 
     * Two separate activities for clean separation:
     * - NATIVE mode: NativeComposeEmulatorActivity (Radial/Lemuroid gamepads)
     * - RETROARCH mode: RetroArchEmulatorActivity (Pure RetroArch overlays)
     * 
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @return Class of emulator activity based on effective mode
     */
    private Class<?> getEmulatorActivityClass(String console) {
        // Use effective mode (game override or console default)
        GamepadPreferenceManager.EmulatorMode effectiveMode = GamepadPreferenceManager.INSTANCE.getEffectiveMode(this, currentConsole, currentGameId);
        
        if (effectiveMode == GamepadPreferenceManager.EmulatorMode.RETROARCH) {
            Log.i(TAG, "Launching RetroArchEmulatorActivity for " + console + "/" + currentGameId);
            return RetroArchEmulatorActivity.class;
        } else {
            Log.i(TAG, "Launching NativeComposeEmulatorActivity for " + console + "/" + currentGameId);
            return NativeComposeEmulatorActivity.class;
        }
    }
    
    /**
     * Setup the emulator mode toggle switch
     */
    private void setupEmulatorModeToggle() {
        // Load console default mode
        GamepadPreferenceManager.EmulatorMode consoleMode = GamepadPreferenceManager.INSTANCE.loadMode(this, currentConsole);
        
        // Load effective mode (game override or console default)
        GamepadPreferenceManager.EmulatorMode effectiveMode = GamepadPreferenceManager.INSTANCE.getEffectiveMode(this, currentConsole, currentGameId);
        
        // Check if game has an override
        boolean hasOverride = GamepadPreferenceManager.INSTANCE.hasGameOverride(this, currentConsole, currentGameId);
        
        // Set switch state (true = RETROARCH, false = NATIVE)
        emulatorModeSwitch.setChecked(effectiveMode == GamepadPreferenceManager.EmulatorMode.RETROARCH);
        
        // Update console default info
        String consoleModeLabel = consoleMode == GamepadPreferenceManager.EmulatorMode.NATIVE ? "NATIVE" : "RETROARCH";
        consoleDefaultInfo.setText("Console default: " + consoleModeLabel);
        
        // Set up switch listener
        emulatorModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GamepadPreferenceManager.EmulatorMode selectedMode = isChecked ? 
                GamepadPreferenceManager.EmulatorMode.RETROARCH : 
                GamepadPreferenceManager.EmulatorMode.NATIVE;
            
            if (selectedMode == consoleMode) {
                // Same as console default, remove override
                GamepadPreferenceManager.INSTANCE.removeGameOverride(this, currentConsole, currentGameId);
                Log.i(TAG, "Removed game override for " + currentGameId + " (now using console default)");
            } else {
                // Different from console default, save override
                GamepadPreferenceManager.INSTANCE.saveGameOverride(this, currentConsole, currentGameId, selectedMode);
                Log.i(TAG, "Saved game override for " + currentGameId + ": " + selectedMode);
            }
            
            // No need to show restart dialog - emulation is not running at this point
        });
        
        Log.i(TAG, "Emulator mode toggle setup - Console: " + consoleMode + ", Effective: " + effectiveMode + ", Has Override: " + hasOverride);
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Maintenir le mode plein écran même après perte de focus
            setupFullscreenMode();
        }
    }

    private void showArtworkUpdateOptions() {
        String[] options = new String[]{"Download missing", "Force refresh"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Artwork Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startArtworkDownloadForGame(true);
                    } else if (which == 1) {
                        startArtworkDownloadForGame(false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void startArtworkDownloadForGame(boolean missingOnly) {
        android.app.ProgressDialog dialog = new android.app.ProgressDialog(this);
        dialog.setMessage(missingOnly ? "Downloading missing artwork..." : "Refreshing artwork...");
        dialog.setCancelable(false);
        dialog.show();
        
        new Thread(() -> {
            try {
                ArtworkDownloadHelper.DownloadResult result = ArtworkDownloadHelper.downloadArtwork(game.getConsole(), game.getBaseName(), !missingOnly, missingOnly);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    if (result.error != null) {
                        Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                    } else if (result.hasAnyDownload()) {
                        Toast.makeText(this, "Artwork updated", Toast.LENGTH_SHORT).show();
                        loadGameImages();
                    } else if (result.skipped) {
                        Toast.makeText(this, "Artwork already present", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Artwork not found on Libretro", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Artwork download failed", e);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.inflate(R.menu.menu_game_details_settings);

        Menu menu = popupMenu.getMenu();
        MenuItem gameInfoItem = menu.findItem(R.id.action_game_info);
        if (gameInfoItem != null) {
            gameInfoItem.setEnabled(currentGameCRC != null && !currentGameCRC.isEmpty());
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_game_info) {
                handleGameInfoAction();
                return true;
            } else if (itemId == R.id.action_update_artwork) {
                showArtworkUpdateOptions();
                return true;
            } else if (itemId == R.id.action_console_settings) {
                openConsoleSettings();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }
    
    private void openConsoleSettings() {
        Intent intent = new Intent(this, ConsoleConfigActivity.class);
        intent.putExtra("console", game.getConsole());
        startActivity(intent);
    }

    private void handleGameInfoAction() {
        if (dismissGameInfoDialog()) {
            return;
        }
        openGameInfoDialog();
    }
    
    private void openGameInfoDialog() {
        if (currentGameCRC == null || currentGameCRC.isEmpty()) {
            Toast.makeText(this, "Game info not available yet", Toast.LENGTH_SHORT).show();
            return;
        }
        GameInfoDialogFragment.show(
            getSupportFragmentManager(),
            currentGameCRC,
            game.getConsole(),
            game.getName(),
            currentGameInfo
        );
        setGameInfoDialogVisible(true);
    }
    
    private GameInfoDialogFragment getGameInfoDialog() {
        return (GameInfoDialogFragment) getSupportFragmentManager().findFragmentByTag(GAME_INFO_DIALOG_TAG);
    }
    
    private boolean dismissGameInfoDialog() {
        GameInfoDialogFragment fragment = getGameInfoDialog();
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
            setGameInfoDialogVisible(false);
            return true;
        }
        return false;
    }
    
    private void registerGameInfoDialogCallbacks() {
        getSupportFragmentManager().setFragmentResultListener(
            GAME_INFO_DIALOG_RESULT_KEY,
            this,
            (requestKey, bundle) -> {
                String event = bundle.getString(GAME_INFO_DIALOG_EVENT_KEY);
                if (GAME_INFO_DIALOG_EVENT_SHOW.equals(event)) {
                    setGameInfoDialogVisible(true);
                } else if (GAME_INFO_DIALOG_EVENT_DISMISS.equals(event)) {
                    setGameInfoDialogVisible(false);
                }
            }
        );
    }
    
    private void syncGameInfoDialogState() {
        boolean dialogVisible = getGameInfoDialog() != null;
        setGameInfoDialogVisible(dialogVisible);
    }
    
    private void setGameInfoDialogVisible(boolean visible) {
        if (isGameInfoDialogVisible == visible) {
            updateGameInfoButtonState();
            return;
        }
        isGameInfoDialogVisible = visible;
        updateGameInfoButtonState();
    }
    
    private void updateGameInfoButtonState() {
        if (gameInfoButton == null) {
            return;
        }
        boolean enabled = currentGameCRC != null && !currentGameCRC.isEmpty();
        gameInfoButton.setEnabled(enabled);
        gameInfoButton.setAlpha(enabled ? 1f : 0.5f);
        gameInfoButton.setChecked(enabled && isGameInfoDialogVisible);
        ThemeManager themeManager = ThemeManager.getInstance(this);
        ColorStateList accent = ColorStateList.valueOf(themeManager.getPrimaryColor(this));
        ColorStateList headerBackground = ColorStateList.valueOf(themeManager.getHeaderBackgroundColor(this));
        ColorStateList iconActive = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.kitt_black));
        if (enabled && isGameInfoDialogVisible) {
            gameInfoButton.setBackgroundTintList(accent);
            gameInfoButton.setIconTint(iconActive);
            gameInfoButton.setStrokeColor(accent);
        } else {
            gameInfoButton.setBackgroundTintList(headerBackground);
            gameInfoButton.setIconTint(accent);
            gameInfoButton.setStrokeColor(accent);
        }
    }

    private String resolveRomPath() {
        final String baseDir = "/storage/emulated/0/GameLibrary-Data/";
        final String consoleDir = getRealConsoleDirectory(game.getConsole());

        // Utiliser directement le chemin local retourné par getFile()
        String localPath = game.getFile();
        if (localPath != null && !localPath.isEmpty()) {
            File directFile = new File(localPath);
            if (directFile.exists()) {
                return directFile.getAbsolutePath();
            }
        }

        // Fallback: utiliser game.getPath() pour construire le chemin
        String rawPath = game.getPath();
        if (rawPath != null && !rawPath.isEmpty()) {
            // Nettoyer le chemin (enlever "./" si présent)
            String cleanPath = rawPath.startsWith("./") ? rawPath.substring(2) : rawPath;
            
            // Essayer avec le chemin complet
            File candidate = new File(baseDir + cleanPath);
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }

            // Essayer avec le répertoire de console
            File remapped = new File(baseDir + consoleDir + "/" + cleanPath);
            if (remapped.exists()) {
                return remapped.getAbsolutePath();
            }
            
            // Extraire juste le nom du fichier et essayer dans le répertoire de console
            int lastSlash = cleanPath.lastIndexOf('/');
            String fileName = lastSlash >= 0 ? cleanPath.substring(lastSlash + 1) : cleanPath;
            File finalCandidate = new File(baseDir + consoleDir + "/" + fileName);
            if (finalCandidate.exists()) {
                return finalCandidate.getAbsolutePath();
            }
            
            // Dernier recours: retourner le chemin même s'il n'existe pas
            return baseDir + consoleDir + "/" + fileName;
        }

        // Si tout échoue, retourner un chemin par défaut
        return baseDir + consoleDir + "/unknown.rom";
    }
}
