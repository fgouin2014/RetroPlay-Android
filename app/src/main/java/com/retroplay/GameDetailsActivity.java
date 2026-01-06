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
import com.retroplay.helpers.RomPathResolver;
import com.retroplay.helpers.MetadataResolver;
import com.retroplay.usecases.LoadGameImagesUseCase;
import com.retroplay.usecases.LaunchGameUseCase;
import com.retroplay.usecases.LoadGameMetadataUseCase;
import com.retroplay.usecases.ManageCoreOverrideUseCase;
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
    private MaterialButton playRetroPlayButton;
    private MaterialButton loadSaveButton;
    private MaterialButton cheatButton;
    private MaterialButton coreOverrideButton;
    private MaterialButton favoriteButton;
    private MaterialButton viewGalleryButton;
    private MaterialButton headerBackButton;
    private MaterialButton headerSettingsButton;
    private MaterialButton headerEditButton;
    private MaterialButton gameInfoButton;
    private TextView headerTitle;
    private FrameLayout pillWasm;
    private FrameLayout pillLoad;
    private FrameLayout pillRetroPlay;
    private LinearLayout retroPlayButtonsContainer;
    private FavoritesManager favoritesManager;

    // Launch Game Use Case
    private LaunchGameUseCase launchGameUseCase;

    // Load Game Metadata Use Case
    private LoadGameMetadataUseCase loadGameMetadataUseCase;

    // Manage Core Override Use Case
    private ManageCoreOverrideUseCase manageCoreOverrideUseCase;

    private String currentGameCRC;
    private String currentPsxSerial;
    private GameInfo currentGameInfo;
    private boolean isGameInfoDialogVisible;
    private String currentGalleryGameId;

    // Emulator Mode Toggle
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

        // Initialiser LoadGameMetadataUseCase
        loadGameMetadataUseCase = new LoadGameMetadataUseCase(this, new LoadGameMetadataUseCase.MetadataCallbacks() {
            @Override
            public void runOnUiThread(Runnable action) {
                GameDetailsActivity.this.runOnUiThread(action);
            }

            @Override
            public void setCurrentPsxSerial(String serial) {
                currentPsxSerial = serial;
            }

            @Override
            public String getCurrentPsxSerial() {
                return currentPsxSerial;
            }
        });

        // Initialiser ManageCoreOverrideUseCase
        manageCoreOverrideUseCase = new ManageCoreOverrideUseCase(this, this::getRealConsoleDirectory);

        // Initialiser LaunchGameUseCase
        launchGameUseCase = new LaunchGameUseCase(this, new LaunchGameUseCase.LaunchCallbacks() {
            @Override
            public void showToast(String message, int duration) {
                Toast.makeText(GameDetailsActivity.this, message, duration).show();
            }

            @Override
            public void showProgressDialog(String message) {
                // Géré par extractToCacheAsync dans l'Activity
            }

            @Override
            public void dismissProgressDialog() {
                // Géré par extractToCacheAsync dans l'Activity
            }

            @Override
            public void runOnUiThread(Runnable action) {
                GameDetailsActivity.this.runOnUiThread(action);
            }

            @Override
            public void startActivity(Intent intent) {
                Log.i(TAG, "📞 LaunchCallbacks.startActivity() called");
                Log.i(TAG, "   Intent class: " + intent.getComponent());
                Log.i(TAG, "   Intent extras: romPath=" + intent.getStringExtra("romPath") +
                        ", gameName=" + intent.getStringExtra("gameName") +
                        ", console=" + intent.getStringExtra("console"));
                try {
                    GameDetailsActivity.this.startActivity(intent);
                    Log.i(TAG, "✅ startActivity() completed successfully");
                } catch (Exception e) {
                    Log.e(TAG, "❌ ERROR in startActivity(): " + e.getMessage(), e);
                    Toast.makeText(GameDetailsActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public File getCacheDir() {
                return GameDetailsActivity.this.getCacheDir();
            }

            @Override
            public String getRealConsoleDirectory(String consoleName) {
                return GameDetailsActivity.this.getRealConsoleDirectory(consoleName);
            }

            @Override
            public String getCurrentGalleryGameId() {
                return currentGalleryGameId;
            }

            @Override
            public String getCurrentPsxSerial() {
                return currentPsxSerial;
            }
        });

        registerGameInfoDialogCallbacks();

        setupHeader();
        setupViews();
        populateGameDetails();
        setupButtons();

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
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Garder l'écran allumé
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Laisser visible notre en-tête personnalisé
    }

    private void setupHeader() {
        headerBackButton = findViewById(R.id.headerBackButton);
        headerSettingsButton = findViewById(R.id.headerSettingsButton);
        headerEditButton = findViewById(R.id.headerEditButton);
        gameInfoButton = findViewById(R.id.game_info_button);
        favoriteButton = findViewById(R.id.favorite_button);
        headerTitle = findViewById(R.id.headerTitle);

        headerTitle.setText(game.getName());
        headerBackButton.setOnClickListener(v -> finish());
        headerSettingsButton.setOnClickListener(this::showSettingsMenu);

        if (headerEditButton != null) {
            headerEditButton.setOnClickListener(v -> showEditGameMetadataDialog());
        }

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
        playRetroPlayButton = findViewById(R.id.play_native_button);
        loadSaveButton = findViewById(R.id.load_save_button);
        cheatButton = findViewById(R.id.cheat_button);
        coreOverrideButton = findViewById(R.id.core_override_button);
        viewGalleryButton = findViewById(R.id.view_gallery_button);
        pillWasm = findViewById(R.id.pill_wasm);
        pillLoad = findViewById(R.id.pill_load);
        pillRetroPlay = findViewById(R.id.pill_native);
        retroPlayButtonsContainer = findViewById(R.id.native_buttons_container);
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

    /**
     * Update game details UI avec métadonnées du gamelist.json
     * Style ES: toutes les métadonnées sont déjà dans le gamelist.json
     */
    private void updateGameDetailsFromGamelist() {
        // Stocker hash pour GameInfoDialog
        // Note: Le hash du gamelist peut ne pas être un CRC32 valide
        // La recherche réelle sera faite dans openGameInfoDialog() en calculant le
        // CRC32 du fichier ROM
        String hash = loadGameMetadataUseCase != null ? loadGameMetadataUseCase.getGameHash(game) : game.getHash();

        // Vérifier si le hash est un CRC32 valide (8 caractères hex)
        if (hash != null && !hash.isEmpty() && hash.length() == 8 && hash.matches("[0-9A-Fa-f]{8}")) {
            currentGameCRC = hash.toUpperCase();
            Log.i(TAG, "[GameInfo] Initialized CRC from gamelist hash: " + currentGameCRC);
        } else {
            // Hash invalide ou non-CRC32, sera calculé plus tard
            currentGameCRC = null;
            Log.d(TAG, "[GameInfo] Hash from gamelist is not a valid CRC32, will calculate from ROM file");
        }

        currentGameInfo = null; // Plus de lookup runtime
        updateGameInfoButtonState();

        // Utiliser LoadGameMetadataUseCase pour mettre à jour les vues
        if (loadGameMetadataUseCase != null) {
            LoadGameMetadataUseCase.MetadataViews views = new LoadGameMetadataUseCase.MetadataViews();
            views.gameDescription = gameDescription;
            views.gameGenre = gameGenre;
            views.gamePlayers = gamePlayers;
            views.gameReleaseDate = gameReleaseDate;
            views.gameRating = gameRating;

            loadGameMetadataUseCase.updateMetadataViews(game, views);
        } else {
            // Fallback si UseCase n'est pas initialisé
            Log.w(TAG, "LoadGameMetadataUseCase not initialized, using direct update");
            gameDescription.setText(MetadataResolver.resolveDescription(game));
            gameGenre.setText(MetadataResolver.resolveGenre(game));
            gamePlayers.setText(MetadataResolver.resolvePlayers(game));
            gameReleaseDate.setText(MetadataResolver.resolveReleaseDate(game));
            if (gameRating != null) {
                String rating = MetadataResolver.resolveRating(game);
                if (rating != null) {
                    gameRating.setText(rating);
                    gameRating.setVisibility(View.VISIBLE);
                } else {
                    gameRating.setVisibility(View.GONE);
                }
            }
        }
    }

    private void loadGameImages() {
        ImageView screenshotBackground = findViewById(R.id.game_screenshot_background);
        LoadGameImagesUseCase useCase = new LoadGameImagesUseCase();
        useCase.loadGameImages(game, screenshotBackground, gameImage);
    }

    private void setupButtons() {
        // Play button (WASM)
        playButton.setOnClickListener(v -> launchGame());

        // Play RetroPlay button
        playRetroPlayButton.setOnClickListener(v -> launchGameRetroPlay(0)); // 0 = nouvelle partie

        // Load save button (ouvre menu de sélection de slot)
        loadSaveButton.setOnClickListener(v -> showSlotSelectionDialog());

        // Cheat button (ouvre interface de codes de triche)
        cheatButton.setOnClickListener(v -> openCheatActivity());

        // Core override button (change le core utilisé pour ce jeu)
        coreOverrideButton.setOnClickListener(v -> showCoreOverrideDialog());
        updateCoreOverrideButton();

        viewGalleryButton.setOnClickListener(v -> openScreenshotGallery());

        // Afficher les boutons RetroPlay pour TOUTES les consoles
        String console = game.getConsole().toLowerCase();
        retroPlayButtonsContainer.setVisibility(View.VISIBLE);

        // Vérifier si des sauvegardes existent dans les slots
        checkAndShowLoadSaveButton();

        // Dual/Triple-Color Pill - WASM (Red), LOAD (Blue), and RetroPlay (Green)
        pillWasm.setOnClickListener(v -> {
            Log.i(TAG, "WASM pill clicked - launching EmulatorJS");
            launchGame();
        });

        pillLoad.setOnClickListener(v -> {
            Log.i(TAG, "LOAD pill clicked - opening slot selection");
            showSlotSelectionDialog();
        });

        pillRetroPlay.setOnClickListener(v -> {
            Log.i(TAG, "RETROPLAY pill clicked - launching RetroArch");
            launchGameRetroPlay(0); // 0 = new game
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rafraîchir le bouton de core override au cas où il aurait été changé
        // depuis l'activité d'émulation
        updateCoreOverrideButton();
        if (favoriteButton != null) {
            updateFavoriteButton();
        }
        syncGameInfoDialogState();

        // Réappliquer le thème au cas où il aurait changé
        applyTheme();
    }

    private void launchGame() {
        if (launchGameUseCase != null) {
            launchGameUseCase.launchWasm(game);
        } else {
            // Fallback si UseCase n'est pas initialisé
            Log.e(TAG, "LaunchGameUseCase not initialized");
            Toast.makeText(this, "Error: Launch system not ready", Toast.LENGTH_SHORT).show();
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

    private void launchGameRetroPlay(int slot) {
        Log.i(TAG, "🎮 launchGameRetroPlay() called with slot: " + slot);
        if (launchGameUseCase != null) {
            Log.i(TAG, "✅ LaunchGameUseCase is initialized");
            // Vérifier si extraction nécessaire (géré par le UseCase)
            String romPath = RomPathResolver.resolveRomPath(game);
            String fileName = romPath;
            int lastSlash = romPath.lastIndexOf("/");
            if (lastSlash >= 0) {
                fileName = romPath.substring(lastSlash + 1);
            }

            String console = game.getConsole().toLowerCase();
            boolean isNativeCompressedFormat = fileName.endsWith(".pbp") || fileName.endsWith(".chd") ||
                    fileName.endsWith(".cso") || fileName.endsWith(".daa");
            boolean isArcadeZip = (console.startsWith("fbneo") || console.equals("arcade") ||
                    console.equals("mame") || console.equals("neogeo")) && fileName.endsWith(".zip");
            boolean isArchive = (fileName.endsWith(".zip") || fileName.endsWith(".7z")) && !isArcadeZip;

            if (isArchive) {
                // Extraction nécessaire - utiliser la méthode existante
                extractToCacheAsync(romPath, fileName, slot, console);
            } else {
                // Lancement direct via UseCase
                launchGameUseCase.launchRetroArch(game, slot);
            }
        } else {
            // Fallback si UseCase n'est pas initialisé (ne devrait pas arriver)
            Log.e(TAG, "LaunchGameUseCase not initialized");
            Toast.makeText(this, "Error: Launch system not ready", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Extrait un .zip dans le cache en arriere-plan (evite ANR)
     * Toutes les consoles supportees (optionnel, active par l'utilisateur)
     * Par defaut: DESACTIVE (charge ROM directement)
     * Si probleme: L'utilisateur active le cache dans le menu
     */
    private void extractToCacheAsync(final String zipPath, final String zipFileName, final int slot,
            final String console) {
        // Mapper au vrai nom de répertoire
        final String realConsoleDir = getRealConsoleDirectory(console);

        // Utiliser le cache interne de l'application (pas de permissions spéciales
        // nécessaires)
        // Format: /data/data/com.retroplay/cache/roms/{console}/
        final java.io.File appCacheDir = getCacheDir();
        final String cacheDir = appCacheDir.getAbsolutePath() + "/roms/" + realConsoleDir;

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
                targetExtension = ".sfc"; // Ou .smc
                break;
            case "n64":
                targetExtension = ".z64"; // Ou .n64, .v64
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
                targetExtension = ".bin"; // Ou .smd, .md, .gen
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
                targetExtension = ".zip"; // Les ROMs arcade restent en .zip
                break;

            // Default
            default:
                targetExtension = ".bin";
                break;
        }

        // Nom du fichier extrait (sans region)
        // Normaliser le nom pour le cache: enlever les caractères spéciaux (!, :, etc.)
        // qui causent des problèmes
        String simpleName = game.getName().replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
        // Remplacer les caractères spéciaux par des underscores pour éviter les
        // problèmes de fichiers
        // Garder seulement lettres, chiffres, espaces, tirets et underscores
        simpleName = simpleName.replaceAll("[^a-zA-Z0-9\\s\\-_]", "_");
        // Remplacer les espaces multiples par un seul underscore
        simpleName = simpleName.replaceAll("\\s+", "_");
        // Enlever les underscores multiples
        simpleName = simpleName.replaceAll("_+", "_");
        // Enlever les underscores en début/fin
        simpleName = simpleName.replaceAll("^_+|_+$", "");
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
                    boolean created = cacheDirFile.mkdirs();
                    if (!created) {
                        Log.e(TAG, console + ": Failed to create cache directory: " + cacheDir);
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(this, "Error: Cannot create cache directory", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                }
                Log.i(TAG, console + ": Cache directory ready: " + cacheDir);
                Log.i(TAG, console + ": Cached ROM path (normalized): " + cachedRomPath);

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
                    org.apache.commons.compress.archivers.sevenz.SevenZFile sevenZFile = new org.apache.commons.compress.archivers.sevenz.SevenZFile(
                            archiveFile);

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
                        launchWithCachedRom(zipPath, slot); // Fallback au .zip
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
                return entryName.endsWith(".zip"); // Les ROMs arcade sont en .zip
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

        // Fallback: Si le calcul échoue, utiliser le hash du gamelist.json si c'est un
        // CRC32 (8 caractères hex)
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

            com.retroplay.database.GameInfo gameInfo = com.retroplay.database.DatabaseManager.INSTANCE
                    .lookupGame(gameCRC, game.getConsole());
            if (gameInfo != null) {
                Log.i(TAG, "✅ Game identified from database:");
                Log.i(TAG, "  Name: " + gameInfo.getName());
                Log.i(TAG, "  Genre: " + gameInfo.getGenre());
                Log.i(TAG, "  Developer: " + gameInfo.getDeveloper());
                Log.i(TAG, "  Year: " + gameInfo.getReleaseYear());
                Log.i(TAG, "  " + gameInfo.getDisplayInfo());

                // Check cheats available
                java.io.File cheatFile = com.retroplay.database.DatabaseManager.INSTANCE.getCheatsPath(gameInfo,
                        game.getConsole());
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

        // Pass Config ID (Priority: Serial > CRC > Name)
        // This is what the user meant: determine identity HERE, not in emulator
        String configId = gameCRC;
        if (currentPsxSerial != null) {
            configId = currentPsxSerial;
            intent.putExtra("psxSerial", currentPsxSerial); // Helpful context
        } else if (configId == null) {
            // Fallback for config ID if no CRC
            String safeName = game.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            configId = safeName;
        }
        intent.putExtra("configId", configId);
        Log.i(TAG, "🚀 Launching emulator with Config ID: " + configId);

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
        String romPath = RomPathResolver.resolveRomPath(game); // Get the real ROM path

        // Utiliser le nouveau dialog Compose moderne
        GameDetailsDialogHelperKt.showModernSlotDialogFromDetails(this, console, gameName, romPath);
    }

    private void checkAndShowLoadSaveButton() {
        // Vérifier si des sauvegardes existent dans les slots
        String console = game.getConsole();
        String gameName = game.getName();

        Log.d(TAG, "[SAVE DETECTION] Checking saves for: " + gameName + " (console: " + console + ")");

        // Structure : saves/{console}/{gameName}/slot{slot}.state
        java.io.File gameDir = new java.io.File(
                "/storage/emulated/0/GameLibrary-Data/saves/" + console + "/" + gameName);

        boolean hasAnySave = false;
        if (gameDir.exists() && gameDir.isDirectory()) {
            // Chercher les fichiers slot{slot}.state dans le répertoire du jeu
            for (int slot = 1; slot <= 5; slot++) {
                java.io.File saveFile = new java.io.File(gameDir, "slot" + slot + ".state");
                if (saveFile.exists()) {
                    hasAnySave = true;
                    Log.d(TAG, "[SAVE DETECTION] Found save in slot " + slot + ": " + saveFile.getAbsolutePath());
                    break;
                }
            }

            // Si pas trouvé avec le nom exact, chercher n'importe quel fichier slot*.state
            if (!hasAnySave) {
                java.io.File[] stateFiles = gameDir
                        .listFiles((dir, name) -> name.startsWith("slot") && name.endsWith(".state"));
                if (stateFiles != null && stateFiles.length > 0) {
                    hasAnySave = true;
                    Log.d(TAG, "[SAVE DETECTION] Found .state files in game dir: " + stateFiles.length);
                }
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

    // launchWithCustomTabs et generateSlug sont maintenant dans LaunchGameUseCase
    // Conservés pour compatibilité si nécessaire, mais ne sont plus utilisés
    // directement

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

        if (headerEditButton != null) {
            headerEditButton.setIconTint(ColorStateList.valueOf(primaryColor));
            headerEditButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            headerEditButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            headerEditButton.setAlpha(1.0f);
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

        // Les TextViews dans les cards (genre, players, release date) utilisent
        // primaryColor pour être visibles
        if (gameGenre != null) {
            gameGenre.setTextColor(primaryColor);
            gameGenre.setAlpha(1.0f);
        }
        if (gamePlayers != null) {
            gamePlayers.setTextColor(primaryColor);
            gamePlayers.setAlpha(1.0f);
        }
        if (gameReleaseDate != null) {
            gameReleaseDate.setTextColor(primaryColor);
            gameReleaseDate.setAlpha(1.0f);
        }

        // Action buttons
        if (playRetroPlayButton != null) {
            playRetroPlayButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            playRetroPlayButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.kitt_black)));
            playRetroPlayButton.setAlpha(1.0f);
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
            // Note: Button background is typically set via drawable, but we can set text
            // color
        }

        // Pills (FrameLayout) - these might have backgrounds set in XML, but we can
        // ensure text colors
        // Note: Pills are typically styled via their child TextViews

        // Console default info

        // Header background
        android.view.View detailsHeader = findViewById(R.id.details_header);
        if (detailsHeader != null) {
            detailsHeader.setBackgroundColor(headerBackgroundColor);
        }

        // Game Info button
        if (gameInfoButton != null) {
            gameInfoButton.setIconTint(ColorStateList.valueOf(primaryColor));
            gameInfoButton.setBackgroundTintList(ColorStateList.valueOf(headerBackgroundColor));
            gameInfoButton.setStrokeColor(ColorStateList.valueOf(primaryColor));
            gameInfoButton.setAlpha(1.0f);
        }

        // MaterialCardView - appliquer le thème aux cards
        applyThemeToCards(primaryColor, headerBackgroundColor);

        // Favorite and Game Info buttons are handled in their update methods
        updateFavoriteButton();
        updateGameInfoButtonState();
    }

    /**
     * Applique le thème aux MaterialCardView de la page
     */
    private void applyThemeToCards(int primaryColor, int headerBackgroundColor) {
        // Trouver toutes les MaterialCardView dans le layout
        android.view.View rootView = findViewById(android.R.id.content);
        if (rootView instanceof android.view.ViewGroup) {
            applyThemeToCardsRecursive((android.view.ViewGroup) rootView, primaryColor, headerBackgroundColor);
        }
    }

    private void applyThemeToCardsRecursive(android.view.ViewGroup parent, int primaryColor,
            int headerBackgroundColor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);

            if (child instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) child;
                card.setStrokeColor(primaryColor);
                card.setCardBackgroundColor(headerBackgroundColor);
            }

            if (child instanceof android.view.ViewGroup) {
                applyThemeToCardsRecursive((android.view.ViewGroup) child, primaryColor, headerBackgroundColor);
            }
        }
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
        if (coreOverrideButton != null && manageCoreOverrideUseCase != null) {
            String buttonText = manageCoreOverrideUseCase.getCoreOverrideButtonText(game);
            coreOverrideButton.setText(buttonText);
        }
    }

    // getDefaultCoreForConsole est maintenant dans ManageCoreOverrideUseCase

    /**
     * Affiche un dialog pour choisir le core à utiliser pour ce jeu
     */
    private void showCoreOverrideDialog() {
        if (manageCoreOverrideUseCase == null) {
            Log.e(TAG, "ManageCoreOverrideUseCase not initialized");
            return;
        }

        // Préparer les données via le UseCase
        ManageCoreOverrideUseCase.CoreDialogData data = manageCoreOverrideUseCase.prepareCoreDialogData(game);
        if (data == null) {
            Log.e(TAG, "Failed to prepare core dialog data");
            return;
        }

        // Convertir en tableaux
        final String[] cores = data.coresList.toArray(new String[0]);
        final String[] coreIds = data.coreIdsList.toArray(new String[0]);
        final int selectedPosition = data.selectedPosition;
        final String relativePath = data.relativePath;
        final String defaultCoreName = data.defaultCoreName;

        // Créer un ListView personnalisé avec scrolling et checkmarks
        ListView listView = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, cores) {
            @Override
            public boolean isEnabled(int position) {
                // Désactiver les headers (ceux qui ont null dans coreIds, sauf position 0 qui
                // est "Default")
                if (position == 0)
                    return true; // "Default" est toujours activé
                if (position >= coreIds.length)
                    return false;
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

            String coreId = position < coreIds.length ? coreIds[position] : null;
            String coreDisplayName = position < cores.length ? cores[position] : "";

            // Appliquer l'override via le UseCase
            manageCoreOverrideUseCase.applyCoreOverride(relativePath, coreId, coreDisplayName);

            if (coreId == null) {
                Toast.makeText(this, "Using default core: " + defaultCoreName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Core set to: " + coreId.toUpperCase(), Toast.LENGTH_SHORT).show();
            }

            updateCoreOverrideButton();
            dialog.dismiss();
        });

        dialog.show();
    }

    // CoreInfo, loadAvailableCoresForConsole, getCorePatternsForConsole,
    // getFallbackCoresForConsole
    // sont maintenant dans ManageCoreOverrideUseCase

    /**
     * Get the appropriate emulator activity class based on user preference
     *
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @return Class of emulator activity
     */
    private Class<?> getEmulatorActivityClass(String console) {
        // RetroPlay Mode: Always use RetroArchEmulatorActivity
        return RetroArchEmulatorActivity.class;
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
        String[] options = new String[] { "Download missing", "Force refresh" };
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
                ArtworkDownloadHelper.DownloadResult result = ArtworkDownloadHelper.downloadArtwork(game.getConsole(),
                        game.getBaseName(), !missingOnly, missingOnly);
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

    private void showEditGameMetadataDialog() {
        // Obtenir le répertoire de la console
        String consoleDirName = getRealConsoleDirectory(game.getConsole());
        java.io.File consoleDir = new java.io.File("/storage/emulated/0/GameLibrary-Data/roms/" + consoleDirName);

        if (!consoleDir.exists()) {
            android.widget.Toast
                    .makeText(this, "Console directory not found: " + consoleDirName, android.widget.Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Créer le dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View dialogView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_game_metadata, null);

        // Récupérer les champs
        android.widget.EditText titleInput = dialogView.findViewById(R.id.gameTitleInput);
        android.widget.EditText descriptionInput = dialogView.findViewById(R.id.gameDescriptionInput);
        android.widget.EditText genreInput = dialogView.findViewById(R.id.gameGenreInput);
        android.widget.EditText playersInput = dialogView.findViewById(R.id.gamePlayersInput);
        android.widget.EditText releaseDateInput = dialogView.findViewById(R.id.gameReleaseDateInput);
        android.widget.EditText developerInput = dialogView.findViewById(R.id.gameDeveloperInput);
        android.widget.EditText publisherInput = dialogView.findViewById(R.id.gamePublisherInput);

        // Pré-remplir avec les valeurs actuelles
        titleInput.setText(game.getName() != null ? game.getName() : "");
        descriptionInput.setText(game.getDesc() != null ? game.getDesc() : "");
        genreInput.setText(game.getGenre() != null ? game.getGenre() : "");
        playersInput.setText(game.getPlayers() != null ? game.getPlayers() : "");

        // Formater la date (YYYYMMDD)
        String releaseDate = game.getReleasedate();
        if (releaseDate != null && !releaseDate.isEmpty()) {
            // Si la date contient "T", extraire juste la partie YYYYMMDD
            if (releaseDate.contains("T")) {
                releaseDate = releaseDate.substring(0, 8);
            }
            releaseDateInput.setText(releaseDate);
        } else {
            releaseDateInput.setText("");
        }

        developerInput.setText(game.getDeveloper() != null ? game.getDeveloper() : "");
        publisherInput.setText(game.getPublisher() != null ? game.getPublisher() : "");

        // Appliquer le thème
        ThemeManager themeManager = ThemeManager.getInstance(this);
        int primaryColor = themeManager.getPrimaryColor(this);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);

        builder.setView(dialogView);
        builder.setTitle(null); // Pas de titre par défaut, on utilise notre header

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Récupérer les boutons du footer après création du dialog
        com.google.android.material.button.MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        com.google.android.material.button.MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        com.google.android.material.button.MaterialButton deleteButton = dialogView.findViewById(R.id.deleteButton);

        // Configurer les boutons
        saveButton.setOnClickListener(v -> {
            // Récupérer les nouvelles valeurs
            String newTitle = titleInput.getText().toString().trim();
            String newDescription = descriptionInput.getText().toString().trim();
            String newGenre = genreInput.getText().toString().trim();
            String newPlayers = playersInput.getText().toString().trim();
            String newReleaseDate = releaseDateInput.getText().toString().trim();
            String newDeveloper = developerInput.getText().toString().trim();
            String newPublisher = publisherInput.getText().toString().trim();

            // Valider le titre (obligatoire)
            if (newTitle.isEmpty()) {
                android.widget.Toast.makeText(this, "Title cannot be empty", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Formater la date si nécessaire (ajouter T000000 si juste YYYYMMDD)
            if (!newReleaseDate.isEmpty() && newReleaseDate.length() == 8) {
                newReleaseDate = newReleaseDate + "T000000";
            }

            // Créer un GameEntry mis à jour via la fonction helper Kotlin
            com.retroplay.GamelistManager.GameEntry updatedEntry = com.retroplay.GamelistManager.createGameEntry(
                    game.getId(),
                    newTitle,
                    game.getPath(),
                    newDescription.isEmpty() ? null : newDescription,
                    game.getImagePath(),
                    newReleaseDate.isEmpty() ? null : newReleaseDate,
                    newDeveloper.isEmpty() ? null : newDeveloper,
                    newPublisher.isEmpty() ? null : newPublisher,
                    newGenre.isEmpty() ? null : newGenre,
                    newPlayers.isEmpty() ? null : newPlayers,
                    game.getHash(),
                    game.getRating());

            // Mettre à jour dans le gamelist.json
            boolean success = com.retroplay.GamelistManager.updateGameInGamelist(
                    consoleDir,
                    game.getId(),
                    game.getPath(),
                    updatedEntry);

            if (success) {
                // Mettre à jour l'objet Game local
                game.name = newTitle;
                game.desc = newDescription.isEmpty() ? null : newDescription;
                game.genre = newGenre.isEmpty() ? null : newGenre;
                game.players = newPlayers.isEmpty() ? null : newPlayers;
                game.releasedate = newReleaseDate.isEmpty() ? null : newReleaseDate;
                game.developer = newDeveloper.isEmpty() ? null : newDeveloper;
                game.publisher = newPublisher.isEmpty() ? null : newPublisher;

                // Rafraîchir l'affichage
                updateGameDetailsFromGamelist();
                headerTitle.setText(game.getName());

                dialog.dismiss();
                android.widget.Toast.makeText(this, "Game metadata saved", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "Failed to save metadata", android.widget.Toast.LENGTH_SHORT)
                        .show();
            }
        });

        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
        });

        // Bouton DELETE: Supprimer l'entrée du gamelist (pas le fichier ROM ni les
        // images)
        deleteButton.setOnClickListener(v -> {
            // Confirmation avant suppression
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Delete Game Entry")
                    .setMessage("Remove this game from the gamelist?\n\n" +
                            "The ROM file and images will NOT be deleted.\n" +
                            "Only the entry in gamelist.json will be removed.")
                    .setPositiveButton("DELETE", (d, w) -> {
                        // Supprimer l'entrée du gamelist
                        boolean success = com.retroplay.GamelistManager.removeGameFromGamelist(
                                consoleDir,
                                game.getId(),
                                game.getPath());

                        if (success) {
                            dialog.dismiss();
                            android.widget.Toast
                                    .makeText(this, "Game removed from gamelist", android.widget.Toast.LENGTH_SHORT)
                                    .show();
                            // Retourner un résultat pour rafraîchir la liste
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("gameDeleted", true);
                            setResult(RESULT_OK, resultIntent);
                            // Fermer l'activité
                            finish();
                        } else {
                            android.widget.Toast.makeText(this, "Failed to remove game from gamelist",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
        });

        // Appliquer le thème aux boutons du footer
        saveButton.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
        saveButton.setTextColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
        saveButton.setTypeface(android.graphics.Typeface.MONOSPACE);
        cancelButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        cancelButton.setTextColor(android.content.res.ColorStateList.valueOf(primaryColor));
        cancelButton.setTypeface(android.graphics.Typeface.MONOSPACE);
        deleteButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        deleteButton.setTextColor(android.content.res.ColorStateList.valueOf(primaryColor));
        deleteButton.setTypeface(android.graphics.Typeface.MONOSPACE);

        // Appliquer le fond au dialog
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
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
        // Try to calculate CRC32 from ROM file first
        Toast.makeText(this, "🔍 Searching game info...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String calculatedCRC = null;
            com.retroplay.database.GameInfo foundInfo = null;

            // Step 1: Try to calculate CRC32 from ROM file
            String romPath = com.retroplay.helpers.RomPathResolver.resolveRomPath(game);
            if (romPath != null) {
                calculatedCRC = com.retroplay.database.DatabaseManager.INSTANCE.calculateCRC32(romPath);
                if (calculatedCRC != null) {
                    Log.i(TAG, "[GameInfo] Calculated CRC32 from ROM: " + calculatedCRC);
                    foundInfo = com.retroplay.database.DatabaseManager.INSTANCE.lookupGame(calculatedCRC,
                            game.getConsole());
                    if (foundInfo != null) {
                        Log.i(TAG, "✅ Game Info found via calculated CRC32: " + foundInfo.getName());
                    }
                }
            }

            // Step 2: Fallback to hash from gamelist.json if it's a valid CRC32
            if (foundInfo == null && calculatedCRC == null && game.getHash() != null && !game.getHash().isEmpty()) {
                String hash = game.getHash();
                if (hash.length() == 8 && hash.matches("[0-9A-Fa-f]{8}")) {
                    calculatedCRC = hash.toUpperCase();
                    Log.i(TAG, "[GameInfo] Using CRC32 from gamelist.json: " + calculatedCRC);
                    foundInfo = com.retroplay.database.DatabaseManager.INSTANCE.lookupGame(calculatedCRC,
                            game.getConsole());
                    if (foundInfo != null) {
                        Log.i(TAG, "✅ Game Info found via gamelist hash: " + foundInfo.getName());
                    }
                }
            }

            // Step 3: Fallback to name lookup if CRC lookup failed
            if (foundInfo == null) {
                Log.i(TAG, "[GameInfo] CRC lookup failed, trying name lookup: " + game.getName());
                foundInfo = com.retroplay.database.DatabaseManager.INSTANCE.lookupGameByName(game.getName(),
                        game.getConsole());
                if (foundInfo != null) {
                    Log.i(TAG, "✅ Game Info found via Name Fallback: " + foundInfo.getName());
                    // Use the CRC from the found info
                    calculatedCRC = foundInfo.getCrc();
                }
            }

            final String finalCRC = calculatedCRC;
            final com.retroplay.database.GameInfo finalInfo = foundInfo;

            runOnUiThread(() -> {
                if (finalInfo != null && finalCRC != null) {
                    // Update local cache
                    currentGameCRC = finalCRC;
                    currentGameInfo = finalInfo;
                    // Show dialog
                    showGameInfoDialogInternal(finalCRC, finalInfo);
                    // Refresh button (now that we have CRC)
                    updateGameInfoButtonState();
                } else {
                    Log.w(TAG, "❌ Game Info lookup failed for: " + game.getName() + " (Console: " + game.getConsole()
                            + ")");
                    Toast.makeText(this, "Game info not found in database", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void showGameInfoDialogInternal(String crc, com.retroplay.database.GameInfo info) {
        // Determine Config ID (Priority: Serial > CRC > NameFallback)
        String configId = crc;
        if (currentPsxSerial != null) {
            Log.i(TAG, "[Config] Using PSX Serial as Config ID: " + currentPsxSerial);
            configId = currentPsxSerial;
        } else if (configId == null || configId.isEmpty()) {
            // Should not happen here as crc is checked, but safety first
            String safeName = game.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            Log.w(TAG, "[Config] No CRC/Serial. Using Name as ID: " + safeName);
            configId = safeName;
        }

        GameInfoDialogFragment.show(
                getSupportFragmentManager(),
                crc != null ? crc : "", // Lookup ID (for DB info)
                configId, // Config ID (for Per-Game Config)
                game.getConsole(),
                game.getName(),
                info);
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
                });
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
        // ALWAYS ENABLED - We now support Name Fallback lookup if CRC is missing
        boolean enabled = true;

        gameInfoButton.setEnabled(enabled);
        gameInfoButton.setAlpha(enabled ? 1f : 0.5f);
        gameInfoButton.setChecked(isGameInfoDialogVisible); // Just check visibility state
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

}
