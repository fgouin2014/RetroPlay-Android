
package com.retroplay;

import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import android.app.AlertDialog;
import java.util.Arrays;

/**
 * Activity principale pour afficher la liste des jeux
 */
public class GameListActivity extends AppCompatActivity implements GameAdapter.OnGameClickListener {
    private static final String TAG = "GameListActivity";
    
    // UI Components
    private TextView consoleSelectorButton;
    private com.google.android.material.button.MaterialButton consoleConfigButton;
    private com.google.android.material.button.MaterialButton consoleManagerButton;
    private com.google.android.material.button.MaterialButton favoritesButton;
    private RecyclerView recyclerView;
    private EditText searchInput;
    private com.google.android.material.button.MaterialButton searchToggleButton;
    private TextView searchScopeToggle;
    private TextView paginationPrev;
    private TextView paginationInfo;
    private TextView paginationNext;
    private FloatingActionButton fabRandom;
    private ProgressBar loadingProgress;
    private TextView gamesCount;
    private TextView filterChip;
    private com.google.android.material.button.MaterialButton favoritesFilterButton;
    private View emptyState;
    private TextView emptyStateTitle;
    private TextView emptyStateSubtitle;
    private View searchInputLayout;
    private LinearLayout alphabetRow1;
    private LinearLayout alphabetRow2;
    private LinearLayout alphabetRow3;
    
    // Data
    private List<Game> games = new ArrayList<>();
    private List<Game> filteredGames = new ArrayList<>();
    private GameAdapter adapter;
    private FavoritesManager favoritesManager;
    
    // Console Selection
    private String currentConsole = "nes"; // Default: NES (will be overridden by saved preference)
    private List<ConsoleInfo> availableConsoles = new ArrayList<>();
    
    // SharedPreferences pour la persistance
    private android.content.SharedPreferences consolePrefs;
    
    // Flag pour indiquer si c'est le premier chargement (pour éviter de charger les jeux avant que les consoles soient chargées)
    private boolean isFirstLoad = true;
    
    // Console info class
    private static class ConsoleInfo {
        String id;
        String name;
        String fullName;
        String directory;
        
        ConsoleInfo(String id, String name, String fullName, String directory) {
            this.id = id;
            this.name = name;
            this.fullName = fullName;
            this.directory = directory;
        }
    }
    
    // Pagination
    private com.retroplay.managers.PaginationManager paginationManager;
    private int gamesPerPage = 20;
    
    // Theme
    private com.retroplay.helpers.ThemeApplicator themeApplicator;
    
    // Filters
    private com.retroplay.managers.FilterManager filterManager;
    
    // Wrapper methods pour compatibilité (utilisent le manager)
    private String getCurrentLetter() {
        return paginationManager != null ? paginationManager.getCurrentLetter() : "#";
    }
    
    private int getCurrentPage() {
        return paginationManager != null ? paginationManager.getCurrentPage() : 0;
    }
    
    private List<Game> getCurrentPageGames() {
        return paginationManager != null ? paginationManager.getCurrentPageGames() : new ArrayList<>();
    }
    
    private List<Game> getFilteredGames() {
        return paginationManager != null ? paginationManager.getFilteredGames() : filteredGames;
    }
    
    // State
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Mode plein écran - masquer barre d'état et navigation
        com.retroplay.managers.FullscreenManager.getInstance().setupFullscreenMode(this);
        
        setContentView(R.layout.activity_game_list);
        
        setupToolbar();
        setupViews();
        setupRecyclerView();
        
        // Initialiser ThemeApplicator
        themeApplicator = new com.retroplay.helpers.ThemeApplicator(this);
        
        // Initialiser FilterManager
        filterManager = new com.retroplay.managers.FilterManager(new com.retroplay.managers.FilterManager.FilterCallbacks() {
            @Override
            public List<Game> getAllGames() {
                return GameListActivity.this.games;
            }
            
            @Override
            public List<Game> getFavoriteGames() {
                return GameListActivity.this.getFavoriteGames();
            }
            
            @Override
            public void onFilterChanged(List<Game> filteredGames) {
                // Mise à jour gérée par PaginationManager ou directement dans les méthodes
            }
        });
        
        // Démarrer le WebServerService seulement si l'option est activée
        WebServerPreferences webServerPrefs = WebServerPreferences.getInstance(this);
        if (webServerPrefs.shouldStartServer()) {
            startWebServerService();
        } else {
            Log.i(TAG, "WebServer auto-start disabled (will start when needed for WASM games)");
        }
        
        // Install RetroArch overlays (first launch only)
        installRetroArchOverlays();
        
        // Initialiser SharedPreferences pour la persistance du choix de console
        consolePrefs = getSharedPreferences("game_library_prefs", MODE_PRIVATE);
        
        // Initialiser le manager des favoris
        favoritesManager = FavoritesManager.getInstance(this);
        
        // Charger la dernière console sélectionnée (ou "nes" par défaut)
        currentConsole = consolePrefs.getString("last_selected_console", "nes");
        Log.i(TAG, "Restored last selected console: " + currentConsole);
        
        // Créer les répertoires nécessaires
        createRequiredDirectories();
        
        // Copier les HTML par défaut vers le stockage si nécessaire
        copyDefaultHtmlToStorage();
        
        // Copier EmulatorJS data si nécessaire
        copyEmulatorJSData();
        
        // Charger les consoles depuis l'API en arrière-plan
        // NOTE: loadGames() sera appelé automatiquement après que les consoles soient chargées
        // pour éviter une condition de course où availableConsoles serait vide
        loadAvailableConsoles();
        updateConsoleTitle();
        
        // Le scan est maintenant fait dans SplashActivity avant l'ouverture de MainActivity
        // On charge les jeux APRÈS que les consoles soient chargées (voir onConsolesLoaded())
        // Ne pas appeler loadGames() ici pour éviter la condition de course
        
        // Preload databases in background (for faster game info lookup)
        preloadDatabases();
        
        // Appliquer le thème sélectionné
        applyTheme();
    }
    
    /**
     * Preload major console databases in background for faster game info lookup
     */
    private void preloadDatabases() {
        // List of major consoles to preload (most commonly used)
        java.util.List<String> majorConsoles = java.util.Arrays.asList(
            "nes",      // Nintendo Entertainment System
            "snes",     // Super Nintendo
            "genesis",  // Sega Genesis
            "gba",      // Game Boy Advance
            "gbc",      // Game Boy Color
            "gb",       // Game Boy
            "n64",      // Nintendo 64
            "psx"       // PlayStation
        );
        
        Log.i(TAG, "[DB] Starting background preload of databases for " + majorConsoles.size() + " consoles");
        
        // Set up progress callback (optional, for logging)
        com.retroplay.database.DatabaseManager.INSTANCE.setOnLoadProgress(
            new kotlin.jvm.functions.Function3<String, Integer, Integer, kotlin.Unit>() {
                @Override
                public kotlin.Unit invoke(String console, Integer current, Integer total) {
                    if (console.isEmpty()) {
                        Log.i(TAG, "[DB] ✅ Preload complete: " + total + " databases loaded");
                    } else {
                        Log.d(TAG, "[DB] Preloading: " + console + " (" + current + "/" + total + ")");
                    }
                    return kotlin.Unit.INSTANCE;
                }
            }
        );
        
        // Start async preload (non-blocking)
        com.retroplay.database.DatabaseManager.INSTANCE.preloadDatabasesAsync(majorConsoles);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les consoles au cas où elles aient été modifiées dans Console Manager
        loadAvailableConsoles();
        // Réappliquer le thème au cas où il aurait changé
        applyTheme();
    }
    
    private void applyTheme() {
        if (themeApplicator == null) {
            themeApplicator = new com.retroplay.helpers.ThemeApplicator(this);
        }
        
        com.retroplay.helpers.ThemeApplicator.ThemeViews views = new com.retroplay.helpers.ThemeApplicator.ThemeViews();
        views.headerView = findViewById(R.id.consoleSelectorButton);
        views.consoleSelectorButton = consoleSelectorButton;
        views.consoleConfigButton = consoleConfigButton;
        views.consoleManagerButton = consoleManagerButton;
        views.favoritesButton = favoritesButton;
        views.searchToggleButton = searchToggleButton;
        views.searchInput = searchInput;
        views.gamesCount = gamesCount;
        views.filterChip = filterChip;
        views.favoritesFilterButton = favoritesFilterButton;
        views.loadingProgress = loadingProgress;
        views.emptyStateTitle = emptyStateTitle;
        views.emptyStateSubtitle = emptyStateSubtitle;
        views.fabRandom = fabRandom;
        views.paginationFooter = findViewById(R.id.paginationFooter);
        views.paginationPrev = paginationPrev;
        views.paginationInfo = paginationInfo;
        views.paginationNext = paginationNext;
        views.showOnlyFavorites = filterManager != null ? filterManager.isShowOnlyFavorites() : false;
        
        themeApplicator.applyTheme(views);
        
        // Search Scope Toggle (nécessite une logique spécifique)
        updateSearchScopeButton();
        
        // Appliquer aux boutons alphabétiques
        updateAlphabetAvailability();
    }
    

    private void setupToolbar() {
        // Toolbar is now handled by the unified header card
        // No need for separate toolbar setup
    }
    
    private void setupViews() {
               // Initialize views
               consoleSelectorButton = findViewById(R.id.consoleSelectorButton);
               consoleConfigButton = findViewById(R.id.consoleConfigButton);
               consoleManagerButton = findViewById(R.id.consoleManagerButton);
               favoritesButton = findViewById(R.id.favoritesButton);
               searchInput = findViewById(R.id.searchInput);
               searchToggleButton = findViewById(R.id.searchToggleButton);
               searchScopeToggle = findViewById(R.id.searchScopeToggle);
               searchInputLayout = findViewById(R.id.searchInputLayout);
               paginationPrev = findViewById(R.id.paginationPrev);
               paginationInfo = findViewById(R.id.paginationInfo);
               paginationNext = findViewById(R.id.paginationNext);
               alphabetRow1 = findViewById(R.id.alphabetRow1);
               alphabetRow2 = findViewById(R.id.alphabetRow2);
               alphabetRow3 = findViewById(R.id.alphabetRow3);
               fabRandom = findViewById(R.id.fabRandom);
               loadingProgress = findViewById(R.id.loadingProgress);
               gamesCount = findViewById(R.id.gamesCount);
               filterChip = findViewById(R.id.filterChip);
               favoritesFilterButton = findViewById(R.id.favoritesFilterButton);
               emptyState = findViewById(R.id.emptyState);
               emptyStateTitle = findViewById(R.id.emptyStateTitle);
               emptyStateSubtitle = findViewById(R.id.emptyStateSubtitle);
        
        // Setup search functionality
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Empêcher les retours à la ligne
                if (s.toString().contains("\n")) {
                    String filtered = s.toString().replaceAll("\n", "");
                    searchInput.setText(filtered);
                    searchInput.setSelection(filtered.length());
                    return;
                }
                if (paginationManager == null || s.toString().isEmpty()) {
                    // Si recherche vide, revenir à la pagination alphabétique
                    if (paginationManager != null) {
                        paginationManager.filterByLetter(paginationManager.getCurrentLetter());
                    }
                } else {
                    filterGames(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Setup search on Enter key press
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // Masquer le clavier après la recherche
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
        
               // Setup console selector
               consoleSelectorButton.setOnClickListener(v -> showConsoleSelector());
               // Overflow menu (long press, no UI change)
               consoleSelectorButton.setOnLongClickListener(v -> {
                   showOverflowMenuSheet();
                   return true;
               });
               
               // Setup favorites button
               favoritesButton.setOnClickListener(v -> openFavorites());
               
               // Setup console config button
               consoleConfigButton.setOnClickListener(v -> openConsoleConfig());
               
               // Setup console manager button
               consoleManagerButton.setOnClickListener(v -> openConsoleManager());
               
               // Setup search toggle button
               searchToggleButton.setOnClickListener(v -> toggleSearch());
               
               // Setup search scope toggle (console actuelle / toutes)
               searchScopeToggle.setOnClickListener(v -> toggleSearchScope());
               updateSearchScopeButton();
               
               // Setup pagination button
               // Pagination navigation
               paginationPrev.setOnClickListener(v -> {
                   if (paginationManager != null) {
                       paginationManager.goToPreviousPage();
                   }
               });
               paginationNext.setOnClickListener(v -> {
                   if (paginationManager != null) {
                       paginationManager.goToNextPage();
                   }
               });
               
               // Setup random game button
               fabRandom.setOnClickListener(v -> selectRandomGame());
               
               // Setup filter chip
               filterChip.setOnClickListener(v -> showGenreFilter());
               
               // Setup favorites filter button
               favoritesFilterButton.setOnClickListener(v -> toggleFavoritesFilter());
               
               // Setup alphabetical pagination
               paginationManager = new com.retroplay.managers.PaginationManager(
                   this, alphabetRow1, alphabetRow2, alphabetRow3,
                   new com.retroplay.managers.PaginationManager.PaginationCallbacks() {
                       @Override
                       public List<Game> getAllGames() {
                           return games;
                       }
                       
                       @Override
                       public List<Game> getFavoriteGames() {
                           return GameListActivity.this.getFavoriteGames();
                       }
                       
                       @Override
                       public void onFilterChanged(List<Game> filteredGames, List<Game> currentPageGames) {
                           GameListActivity.this.filteredGames = filteredGames;
                           if (adapter != null) {
                               adapter.updateGames(currentPageGames);
                           }
                           updateGamesCount();
                           updateEmptyState();
                       }
                       
                       @Override
                       public void onLetterSelected(String letter) {
                           // Letter selected, already handled by manager
                       }
                       
                       @Override
                       public void onPageChanged(int page) {
                           updatePaginationUI();
                       }
                   }
               );
               paginationManager.setGamesPerPage(gamesPerPage);
               paginationManager.setupAlphabetPagination();
    }

    private void showOverflowMenuSheet() {
        boolean searchAllConsoles = filterManager != null ? filterManager.isSearchAllConsoles() : false;
        com.retroplay.ui.fragments.OverflowMenuSheet.show(
            this,
            searchAllConsoles,
            new com.retroplay.ui.fragments.OverflowMenuSheet.OverflowMenuCallbacks() {
                @Override
                public void onOpenConsoleGallery() {
                    openConsoleGallery();
                }
                
                @Override
                public void onOpenFavorites() {
                    openFavorites();
                }
                
                @Override
                public void onOpenConsoleManager() {
                    openConsoleManager();
                }
                
                @Override
                public void onOpenConsoleConfig() {
                    openConsoleConfig();
                }
                
                @Override
                public void onSelectRandomGame() {
                    selectRandomGame();
                }
                
                @Override
                public void onShowPagination() {
                    showPagination();
                }
                
                @Override
                public void onToggleSearchScope() {
                    toggleSearchScope();
                }
                
                @Override
                public void onShowThemeSelector() {
                    showThemeSelector();
                }
                
                @Override
                public void onStartWebServerService() {
                    startWebServerService();
                }
                
                @Override
                public String onGetLocalIpAddress() {
                    return getLocalIpAddress();
                }
            }
        );
    }
    
    /**
     * Obtient l'adresse IP locale pour affichage
     */
    private String getLocalIpAddress() {
        try {
            java.util.List<java.net.NetworkInterface> interfaces = 
                java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
            for (java.net.NetworkInterface networkInterface : interfaces) {
                java.util.List<java.net.InetAddress> addresses = 
                    java.util.Collections.list(networkInterface.getInetAddresses());
                for (java.net.InetAddress address : addresses) {
                    if (!address.isLoopbackAddress() && address.getAddress().length == 4) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return "localhost";
    }
    
    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        
        // Add spacing between items
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(2, 8, true));
    }

    private void loadGames() {
        if (isLoading) return;
        
        isLoading = true;
        loadingProgress.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                // Trouver le ConsoleInfo correspondant pour obtenir le nom réel du répertoire
                String realConsoleDirectory = currentConsole;
                boolean foundConsoleInfo = false;
                for (ConsoleInfo console : availableConsoles) {
                    if (console.id.equals(currentConsole)) {
                        realConsoleDirectory = console.directory;
                        foundConsoleInfo = true;
                        Log.d(TAG, "Using real directory name: " + realConsoleDirectory + " (for ID: " + currentConsole + ")");
                        break;
                    }
                }
                
                if (!foundConsoleInfo && !availableConsoles.isEmpty()) {
                    Log.w(TAG, "ConsoleInfo not found for " + currentConsole + ", availableConsoles.size=" + availableConsoles.size() + ", using currentConsole as directory");
                    Log.d(TAG, "Available consoles: " + availableConsoles.stream().map(c -> c.id).collect(java.util.stream.Collectors.joining(", ")));
                } else if (availableConsoles.isEmpty()) {
                    Log.w(TAG, "availableConsoles is empty, using currentConsole as directory. This may cause issues if currentConsole is an ID instead of a directory name.");
                }
                
                // Utiliser LoadGamesUseCase pour charger les jeux
                com.retroplay.usecases.LoadGamesUseCase loadGamesUseCase = new com.retroplay.usecases.LoadGamesUseCase(this);
                com.retroplay.usecases.LoadGamesUseCase.LoadGamesResult result = loadGamesUseCase.loadGames(currentConsole, realConsoleDirectory);
                
                if (!result.success) {
                    runOnUiThread(() -> {
                        isLoading = false;
                        loadingProgress.setVisibility(View.GONE);
                        emptyState.setVisibility(View.VISIBLE);
                        android.widget.Toast.makeText(GameListActivity.this, 
                            "No games found for " + currentConsole.toUpperCase() + " (" + result.errorMessage + ")", 
                            android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                // Afficher les jeux chargés
                displayGames(result.games);

            } catch (Exception e) {
                Log.e(TAG, "Erreur chargement liste des jeux", e);
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    isLoading = false;
                    showError("Erreur lors du chargement des jeux");
                });
            }
        }).start();
    }
    
    /**
     * Affiche les jeux chargés dans l'UI
     * Les jeux sont déjà parsés et initialisés par LoadGamesUseCase
     */
    private void displayGames(List<Game> loadedGames) {
        try {
            // Sort games alphabetically
            List<Game> tempGames = new ArrayList<>(loadedGames);
            tempGames.sort((g1, g2) -> g1.getName().compareToIgnoreCase(g2.getName()));
            
            runOnUiThread(() -> {
                games = tempGames;
                
                // Mettre à jour l'état favori de chaque jeu
                updateFavoriteStates(games);
                
                // Auto-selectionner la premiere lettre disponible
                if (paginationManager != null) {
                    paginationManager.autoSelectFirstAvailableLetter();
                }
                
                // L'adapter sera mis à jour via le callback du manager
                List<Game> initialGames = paginationManager != null ? paginationManager.getCurrentPageGames() : new ArrayList<>();
                adapter = new GameAdapter(initialGames, this);
                adapter.setFavoritesManager(favoritesManager);
                recyclerView.setAdapter(adapter);
                
                updateGamesCount();
                loadingProgress.setVisibility(View.GONE);
                isLoading = false;
                
                Log.i(TAG, "Liste des jeux chargée: " + games.size() + " jeux (console: " + currentConsole + ")");
            });
        } catch (Exception e) {
            Log.e(TAG, "Error displaying games", e);
            runOnUiThread(() -> {
                loadingProgress.setVisibility(View.GONE);
                isLoading = false;
                showError("Error displaying games list");
            });
        }
    }

    @Override
    public void onClick(Game game) {
        Log.i(TAG, "Jeu sélectionné: " + game.getTitle());
        
        Intent intent = new Intent(this, GameDetailsActivity.class);
        intent.putExtra("game", game);
        startActivityForResult(intent, 200); // Request code 200 pour GameDetailsActivity
    }
    
    /**
     * Met à jour l'état favori de tous les jeux
     */
    private void updateFavoriteStates(List<Game> gameList) {
        if (favoritesManager == null) {
            return;
        }
        
        for (Game game : gameList) {
            game.setFavorite(favoritesManager.isFavorite(game));
        }
    }
    
    /**
     * Obtient la liste des jeux favoris depuis toutes les consoles
     */
    private List<Game> getFavoriteGames() {
        List<Game> favorites = new ArrayList<>();
        if (favoritesManager == null) {
            return favorites;
        }
        
        // Si on est déjà en train de charger, retourner les favoris de la console actuelle uniquement
        // pour éviter les appels récursifs pendant le chargement
        if (isLoading) {
            for (Game game : games) {
                if (favoritesManager.isFavorite(game)) {
                    favorites.add(game);
                }
            }
            return favorites;
        }
        
        // Utiliser LoadFavoritesUseCase pour charger les favoris depuis toutes les consoles
        com.retroplay.usecases.LoadFavoritesUseCase loadFavoritesUseCase = 
            new com.retroplay.usecases.LoadFavoritesUseCase(this, consolePrefs);
        com.retroplay.usecases.LoadFavoritesUseCase.LoadFavoritesResult result = 
            loadFavoritesUseCase.loadAllFavorites();
        
        if (result.success && result.favorites != null) {
            favorites = result.favorites;
        } else {
            Log.w(TAG, "Failed to load favorites via UseCase: " + result.errorMessage);
            // Fallback sur les jeux de la console actuelle
            if (!games.isEmpty()) {
                Log.d(TAG, "Falling back to current console favorites");
                for (Game game : games) {
                    if (favoritesManager.isFavorite(game)) {
                        favorites.add(game);
                    }
                }
            }
        }
        
        Log.i(TAG, "Total favorites loaded: " + favorites.size());
        return favorites;
    }
    
    /**
     * Toggle le filtre des favoris
     */
    private void toggleFavoritesFilter() {
        if (filterManager == null) return;
        
        filterManager.toggleFavoritesFilter();
        boolean showOnlyFavorites = filterManager.isShowOnlyFavorites();
        
        // Mettre à jour l'apparence du bouton
        ThemeManager themeManager = ThemeManager.getInstance(this);
        if (showOnlyFavorites) {
            favoritesFilterButton.setIconResource(R.drawable.ic_favorite_24);
            favoritesFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(themeManager.getPrimaryColor(this)));
            favoritesFilterButton.setIconTintResource(R.color.kitt_black);
            Log.i(TAG, "Favorites filter ENABLED");
        } else {
            favoritesFilterButton.setIconResource(R.drawable.ic_favorite_border_24);
            favoritesFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(themeManager.getHeaderBackgroundColor(this)));
            int primaryColorResId = themeManager.getPrimaryColorResId(this);
            if (primaryColorResId != 0) {
                favoritesFilterButton.setIconTintResource(primaryColorResId);
            } else {
                favoritesFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(themeManager.getPrimaryColor(this)));
            }
            Log.i(TAG, "Favorites filter DISABLED");
        }
        
        // Réappliquer le filtre alphabétique actuel avec la nouvelle liste de base
        if (paginationManager != null) {
            paginationManager.setShowOnlyFavorites(showOnlyFavorites);
            paginationManager.filterByLetter(paginationManager.getCurrentLetter());
        }
    }
    
    private void filterGames(String query) {
        if (filterManager != null) {
            filterManager.applySearchQuery(query);
        }
        
        if (query.isEmpty()) {
            // Retour à la pagination alphabétique
            if (paginationManager != null) {
                paginationManager.filterByLetter(paginationManager.getCurrentLetter());
            }
            return;
        }
        
        boolean searchAllConsoles = filterManager != null ? filterManager.isSearchAllConsoles() : false;
        if (searchAllConsoles) {
            // Recherche dans TOUTES les consoles
            searchInAllConsoles(query);
        } else {
            // Recherche dans la console actuelle seulement
            com.retroplay.usecases.SearchGamesUseCase searchUseCase = new com.retroplay.usecases.SearchGamesUseCase(this);
            com.retroplay.usecases.SearchGamesUseCase.SearchResult result = searchUseCase.searchInCurrentConsole(query, games);
            
            if (result.success) {
                filteredGames.clear();
                filteredGames.addAll(result.games);
                
                // CRITIQUE: Mettre à jour l'adapter directement avec les résultats de recherche
                // Le paginationManager n'est pas utilisé pendant la recherche
                if (adapter != null) {
                    adapter.updateGames(filteredGames);
                }
                
                updateGamesCount();
                updateEmptyState();
            } else {
                Log.e(TAG, "Search failed: " + result.errorMessage);
            }
        }
    }
    
    private void searchInAllConsoles(String query) {
        if (isLoading) return;
        
        isLoading = true;
        loadingProgress.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                // Créer un wrapper pour ConsoleInfo pour implémenter ConsoleInfoLike
                List<com.retroplay.usecases.SearchGamesUseCase.ConsoleInfoLike> consoleInfoList = new ArrayList<>();
                for (ConsoleInfo console : availableConsoles) {
                    consoleInfoList.add(new com.retroplay.usecases.SearchGamesUseCase.ConsoleInfoLike() {
                        @Override
                        public String getId() { return console.id; }
                        @Override
                        public String getName() { return console.name; }
                        @Override
                        public String getFullName() { return console.fullName; }
                        @Override
                        public String getDirectory() { return console.directory; }
                    });
                }
                
                // Utiliser SearchGamesUseCase pour la recherche globale
                com.retroplay.usecases.SearchGamesUseCase searchUseCase = new com.retroplay.usecases.SearchGamesUseCase(this);
                com.retroplay.usecases.SearchGamesUseCase.SearchResult result = searchUseCase.searchAllConsoles(query, consoleInfoList);
                
                if (result.success) {
                    runOnUiThread(() -> {
                        filteredGames.clear();
                        filteredGames.addAll(result.games);
                        
                        // CRITIQUE: Mettre à jour l'adapter directement avec les résultats de recherche
                        if (adapter != null) {
                            adapter.updateGames(filteredGames);
                        }
                        
                        updateGamesCount();
                        updateEmptyState();
                        loadingProgress.setVisibility(View.GONE);
                        isLoading = false;
                        
                        Log.i(TAG, "Global search for '" + query + "': " + result.games.size() + " results");
                    });
                } else {
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        isLoading = false;
                        Log.e(TAG, "Search failed: " + result.errorMessage);
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in global search", e);
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    isLoading = false;
                });
            }
        }).start();
    }
    
    private void toggleSearch() {
        if (searchInputLayout.getVisibility() == View.GONE) {
            searchInputLayout.setVisibility(View.VISIBLE);
            searchToggleButton.setIconResource(R.drawable.ic_close_24);
            
            // Afficher le mode de recherche actuel
            updateSearchPlaceholder();
            
            // Focus sur le textbox et déployer le clavier
            searchInput.requestFocus();
            searchInput.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        } else {
            searchInputLayout.setVisibility(View.GONE);
            searchToggleButton.setIconResource(R.drawable.ic_search_24);
            searchInput.setText(""); // Clear search
            filterGames(""); // Reset filter
            
            // Masquer le clavier
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        }
    }
    
    private void updateSearchPlaceholder() {
        boolean searchAllConsoles = filterManager != null ? filterManager.isSearchAllConsoles() : false;
        if (searchAllConsoles) {
            searchInput.setHint("Rechercher dans TOUTES les consoles...");
        } else {
            String consoleName = currentConsole.toUpperCase();
            searchInput.setHint("Rechercher dans " + consoleName + "...");
        }
    }
    
    private void toggleSearchScope() {
        if (filterManager == null) return;
        
        boolean searchAllConsoles = !filterManager.isSearchAllConsoles();
        filterManager.setSearchScope(searchAllConsoles);
        updateSearchScopeButton();
        updateSearchPlaceholder();
        
        // Relancer la recherche si du texte est présent
        String query = searchInput.getText().toString();
        if (!query.isEmpty()) {
            filterGames(query);
        }
        
        Log.i(TAG, "Search scope changed to: " + (searchAllConsoles ? "ALL CONSOLES" : "CURRENT CONSOLE"));
    }
    
    private void updateSearchScopeButton() {
        if (searchScopeToggle != null) {
            ThemeManager themeManager = ThemeManager.getInstance(this);
            int primaryColor = themeManager.getPrimaryColor(this);
            int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);
            float density = getResources().getDisplayMetrics().density;
            
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable scopeDrawable = new android.graphics.drawable.GradientDrawable();
            scopeDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            scopeDrawable.setCornerRadius(6 * density); // 6dp
            scopeDrawable.setColor(headerBackgroundColor);
            scopeDrawable.setStroke((int)(1 * density), primaryColor); // 1dp stroke with theme color
            searchScopeToggle.setBackground(scopeDrawable);
            searchScopeToggle.setAlpha(1.0f);
            
            boolean searchAllConsoles = filterManager != null ? filterManager.isSearchAllConsoles() : false;
            if (searchAllConsoles) {
                searchScopeToggle.setText("ALL");
                searchScopeToggle.setTextColor(getResources().getColor(R.color.kitt_green));
            } else {
                String shortName = currentConsole.length() > 4 ? currentConsole.substring(0, 4).toUpperCase() : currentConsole.toUpperCase();
                searchScopeToggle.setText(shortName);
                searchScopeToggle.setTextColor(themeManager.getTextPrimaryColor(this));
            }
        }
    }
    
    private void filterByLetter(String letter) {
        if (paginationManager != null) {
            paginationManager.filterByLetter(letter);
        }
    }
    
    private void autoSelectFirstAvailableLetter() {
        if (paginationManager != null) {
            paginationManager.autoSelectFirstAvailableLetter();
        }
    }
    
    private void updateAlphabetAvailability() {
        if (paginationManager != null) {
            paginationManager.updateAlphabetAvailability();
        }
    }
    
    private void showPagination() {
        if (paginationManager == null) return;
        
        com.retroplay.managers.PaginationManager.PaginationInfo info = paginationManager.getPaginationInfo();
        if (info.totalGames <= gamesPerPage) return;
        
        String[] pages = new String[info.totalPages];
        for (int i = 0; i < info.totalPages; i++) {
            int start = i * gamesPerPage + 1;
            int end = Math.min((i + 1) * gamesPerPage, info.totalGames);
            pages[i] = "Page " + (i + 1) + " (" + start + "-" + end + ")";
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pagination - " + paginationManager.getCurrentLetter())
               .setItems(pages, (dialog, which) -> {
                   // Le manager gère la pagination
                   if (paginationManager != null) {
                       // TODO: Implémenter setCurrentPage dans le manager si nécessaire
                   }
                   if (adapter != null) {
                       adapter.notifyDataSetChanged();
                   }
                   updateGamesCount();
               });
        builder.show();
    }
    
    private void selectRandomGame() {
        List<Game> pageGames = paginationManager != null ? paginationManager.getCurrentPageGames() : new ArrayList<>();
        if (pageGames.isEmpty()) {
            showError("Aucun jeu disponible");
            return;
        }
        
        int randomIndex = (int) (Math.random() * pageGames.size());
        Game randomGame = pageGames.get(randomIndex);
        
        // Show snackbar with random game
        ThemeManager themeManager = ThemeManager.getInstance(this);
        Snackbar.make(recyclerView, "Jeu aléatoire: " + randomGame.getName(), Snackbar.LENGTH_LONG)
                .setAction("JOUER", v -> onClick(randomGame))
                .setActionTextColor(themeManager.getPrimaryColor(this))
                .show();
    }
    
    private void openConsoleConfig() {
        Intent intent = new Intent(this, ConsoleConfigActivity.class);
        intent.putExtra("console", currentConsole);
        startActivity(intent);
    }
    
    private void openConsoleManager() {
        Intent intent = new Intent(this, ConsoleManagerActivity.class);
        // Passer la console actuelle pour scroller automatiquement vers elle
        intent.putExtra("scrollToConsole", currentConsole);
        startActivityForResult(intent, 100); // Request code 100 pour Console Manager
    }
    
    private void openFavorites() {
        Intent intent = new Intent(this, FavoritesActivity.class);
        startActivity(intent);
        Log.i(TAG, "Opening Favorites page");
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Retour du Console Manager - rafraîchir la liste
        if (requestCode == 100) {
            Log.i(TAG, "Retour du Console Manager - Rafraîchissement de la liste");
            
            // Vérifier si un gamelist a été généré
            if (data != null && data.getBooleanExtra("gamelistGenerated", false)) {
                String consoleId = data.getStringExtra("consoleId");
                Log.i(TAG, "Gamelist généré pour: " + consoleId);
                
                // Si c'est la console actuelle, recharger les jeux
                if (consoleId != null && consoleId.equals(currentConsole)) {
                    Log.i(TAG, "Rafraîchissement des jeux pour la console actuelle: " + currentConsole);
                    games.clear();
                    filteredGames.clear();
                    loadGames();
                }
            }
            
            // Recharger les consoles disponibles
            loadAvailableConsoles();
            
            // Recharger les jeux de la console actuelle si pas déjà fait
            if (data == null || !data.getBooleanExtra("gamelistGenerated", false)) {
                games.clear();
                filteredGames.clear();
                loadGames();
            }
        }
    }
    
    private void showConsoleSelector() {
        if (availableConsoles.isEmpty()) {
            android.widget.Toast.makeText(this, "Chargement des consoles...", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choisir une console");
        
        // Construire la liste avec séparateurs pour les sous-consoles
        // Le parent est affiché AVANT ses sous-consoles (comme avant)
        List<String> itemsList = new ArrayList<>();
        List<Integer> selectableIndices = new ArrayList<>();
        int currentIndex = -1;
        int displayIndex = 0;
        
        String lastParent = "";
        for (int i = 0; i < availableConsoles.size(); i++) {
            ConsoleInfo console = availableConsoles.get(i);
            boolean isSubConsole = console.id.contains("/");
            
            if (isSubConsole) {
                String parent = console.id.substring(0, console.id.indexOf("/"));
                
                // Ajouter un séparateur si on change de parent
                if (!parent.equals(lastParent)) {
                    if (!lastParent.isEmpty()) {
                        itemsList.add("─────────────────────");
                        displayIndex++;
                    }
                    lastParent = parent;
                }
                
                // Indenter les sous-consoles
                itemsList.add("  └─ " + console.fullName);
            } else {
                lastParent = "";
                // Afficher le fullName avec le nom du répertoire si différent pour éviter les doublons
                String displayName = console.fullName;
                // Si le répertoire diffère du nom normalisé, ajouter le répertoire pour distinguer
                String normalizedId = com.retroplay.ConsoleNameMapper.normalizeToCanonical(console.directory);
                if (!console.directory.equalsIgnoreCase(normalizedId) && !console.directory.equalsIgnoreCase(console.id)) {
                    displayName = console.fullName + " (" + console.directory.toUpperCase() + ")";
                }
                itemsList.add(displayName);
            }
            
            selectableIndices.add(i);
            
            if (console.id.equals(currentConsole)) {
                currentIndex = displayIndex;
            }
            displayIndex++;
        }
        
        String[] items = itemsList.toArray(new String[0]);
        
        builder.setSingleChoiceItems(items, currentIndex, (dialog, which) -> {
            // Vérifier si c'est un séparateur
            if (items[which].startsWith("─────")) {
                return; // Séparateur non cliquable
            }
            
            int consoleIndex = selectableIndices.get(which);
            ConsoleInfo selectedConsole = availableConsoles.get(consoleIndex);
            if (!selectedConsole.id.equals(currentConsole)) {
                switchToConsole(selectedConsole.id);
            }
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss());
        builder.setNeutralButton("GALLERY", (dialog, which) -> {
            dialog.dismiss();
            openConsoleGallery();
        });
        builder.show();
    }

    private void openConsoleGallery() {
        Intent intent = new Intent(this, com.retroplay.gallery.ScreenshotGalleryActivity.class);
        intent.putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_CONSOLE, currentConsole);
        intent.putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_GAME_ID, com.retroplay.gallery.ScreenshotRepository.ALL_GAMES_KEY);
        // Title will be derived in activity when ALL_GAMES_KEY is used
        startActivity(intent);
    }
    
    private void showThemeSelector() {
        ThemeManager.Theme currentTheme = ThemeManager.getInstance(this).getCurrentTheme();
        ThemeManager.Theme[] themes = ThemeManager.Theme.values();
        String[] themeNames = new String[themes.length];
        int currentIndex = 0;
        
        for (int i = 0; i < themes.length; i++) {
            themeNames[i] = themes[i].getDisplayName();
            if (themes[i] == currentTheme) {
                currentIndex = i;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Theme");
        builder.setSingleChoiceItems(themeNames, currentIndex, (dialog, which) -> {
            ThemeManager.Theme selected = themes[which];
            ThemeManager.getInstance(this).setTheme(selected);
            dialog.dismiss();
            
            // Show toast and restart activity to apply theme
            Toast.makeText(this, "Theme changed to " + selected.getDisplayName() + ". Restarting...", Toast.LENGTH_SHORT).show();
            
            // Restart activity to apply theme changes
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }, 500);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    private void switchToConsole(String console) {
        currentConsole = console;
        updateConsoleTitle();
        
        // Sauvegarder le choix de console dans SharedPreferences
        consolePrefs.edit().putString("last_selected_console", console).apply();
        Log.i(TAG, "Console selection saved: " + console);
        
        // Clear current data
        games.clear();
        filteredGames.clear();
        
        // Reset pagination manager
        if (paginationManager != null) {
            paginationManager.filterByLetter("#");
        }
        
        // Marquer que ce n'est plus le premier chargement (pour éviter de charger deux fois)
        isFirstLoad = false;
        
        // Load games for new console
        loadGames();
    }
    
    /**
     * Appelé après que les consoles sont chargées (premier chargement uniquement)
     * Charge les jeux pour la console sélectionnée
     */
    private void onConsolesLoaded() {
        if (isFirstLoad) {
            isFirstLoad = false;
            Log.i(TAG, "Consoles chargées, chargement des jeux pour " + currentConsole);
            loadGames();
        } else {
            Log.d(TAG, "onConsolesLoaded() appelé mais isFirstLoad=false, ignoré");
        }
    }
    
    private void loadAvailableConsoles() {
        // OPTIMISATION: Charger depuis le cache d'abord, puis scanner en arrière-plan si nécessaire
        // Gain de performance: 0-50ms au lieu de 500ms-2s
        com.retroplay.usecases.LoadConsolesUseCase loadConsolesUseCase = 
            new com.retroplay.usecases.LoadConsolesUseCase(this, consolePrefs);
        
        com.retroplay.usecases.LoadConsolesUseCase.CacheLoadResult cacheResult = loadConsolesUseCase.loadFromCache();
        
        if (cacheResult.fromCache && cacheResult.cacheValid) {
            // Convertir ConsoleInfo du UseCase vers ConsoleInfo de l'Activity
            availableConsoles = new ArrayList<>();
            for (com.retroplay.usecases.LoadConsolesUseCase.ConsoleInfo useCaseConsole : cacheResult.consoles) {
                availableConsoles.add(new ConsoleInfo(
                    useCaseConsole.id,
                    useCaseConsole.name,
                    useCaseConsole.fullName,
                    useCaseConsole.directory
                ));
            }
            
            Log.i(TAG, "Loaded " + availableConsoles.size() + " consoles from cache");
            
            // Scanner les sous-consoles en arrière-plan
            scanAndAddSubconsoles();
            
            // Charger les jeux immédiatement avec le cache
            onConsolesLoaded();
            
            // Rescanner en arrière-plan pour mettre à jour le cache
            new Thread(() -> {
                setDefaultConsoles();
            }).start();
            return;
        }
        
        // Pas de cache valide, scanner directement
        Log.i(TAG, "No valid cache, scanning consoles directly (optimized - no WebServer dependency)");
        setDefaultConsoles();
    }
    
    private void saveConsolesToCache(List<ConsoleInfo> consoles) {
        com.retroplay.usecases.LoadConsolesUseCase loadConsolesUseCase = 
            new com.retroplay.usecases.LoadConsolesUseCase(this, consolePrefs);
        
        // Convertir ConsoleInfo de l'Activity vers ConsoleInfo du UseCase
        List<com.retroplay.usecases.LoadConsolesUseCase.ConsoleInfo> useCaseConsoles = new ArrayList<>();
        for (ConsoleInfo console : consoles) {
            useCaseConsoles.add(new com.retroplay.usecases.LoadConsolesUseCase.ConsoleInfo(
                console.id,
                console.name,
                console.fullName,
                console.directory
            ));
        }
        
        loadConsolesUseCase.saveToCache(useCaseConsoles);
    }
    
    private void scanAndAddSubconsoles() {
        // Scanner les sous-consoles localement et les ajouter à availableConsoles
        new Thread(() -> {
            try {
                com.retroplay.usecases.ScanGamesUseCase scanUseCase = new com.retroplay.usecases.ScanGamesUseCase(this);
                List<com.retroplay.usecases.ScanGamesUseCase.ConsoleInfo> subconsoles = scanUseCase.scanSubconsoles();
                
                // Ajouter les sous-consoles à availableConsoles sur le thread UI (sans doublons)
                runOnUiThread(() -> {
                    for (com.retroplay.usecases.ScanGamesUseCase.ConsoleInfo useCaseSubconsole : subconsoles) {
                        // Convertir vers ConsoleInfo de l'Activity
                        ConsoleInfo subconsole = new ConsoleInfo(
                            useCaseSubconsole.id,
                            useCaseSubconsole.name,
                            useCaseSubconsole.fullName,
                            useCaseSubconsole.directory
                        );
                        
                        // Vérifier si la console existe déjà
                        boolean exists = false;
                        for (ConsoleInfo existing : availableConsoles) {
                            if (existing.id.equals(subconsole.id)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            availableConsoles.add(subconsole);
                            Log.i(TAG, "Sous-console ajoutée: " + subconsole.id);
                        } else {
                            Log.i(TAG, "Sous-console déjà présente: " + subconsole.id);
                        }
                    }
                    // Trier pour regrouper les sous-consoles après leur parent
                    availableConsoles.sort((a, b) -> {
                        boolean aIsSub = a.id.contains("/");
                        boolean bIsSub = b.id.contains("/");
                        
                        if (aIsSub && bIsSub) {
                            // Les deux sont des sous-consoles - comparer par parent puis par nom
                            String aParent = a.id.substring(0, a.id.indexOf("/"));
                            String bParent = b.id.substring(0, b.id.indexOf("/"));
                            int parentCompare = aParent.compareTo(bParent);
                            if (parentCompare != 0) {
                                return parentCompare;
                            }
                            // Même parent, comparer les noms de sous-consoles
                            String aSub = a.id.substring(a.id.indexOf("/") + 1);
                            String bSub = b.id.substring(b.id.indexOf("/") + 1);
                            return aSub.compareTo(bSub);
                        } else if (aIsSub) {
                            // a est une sous-console, b est un parent
                            String aParent = a.id.substring(0, a.id.indexOf("/"));
                            int parentCompare = aParent.compareTo(b.id);
                            if (parentCompare == 0) {
                                return 1; // Sous-console après son parent
                            }
                            return parentCompare;
                        } else if (bIsSub) {
                            // b est une sous-console, a est un parent
                            String bParent = b.id.substring(0, b.id.indexOf("/"));
                            int parentCompare = a.id.compareTo(bParent);
                            if (parentCompare == 0) {
                                return -1; // Parent avant sa sous-console
                            }
                            return parentCompare;
                        } else {
                            // Les deux sont des parents - tri alphabétique simple
                            return a.id.compareTo(b.id);
                        }
                    });
                    Log.i(TAG, "Total après ajout sous-consoles: " + availableConsoles.size());
                });
            } catch (Exception e) {
                Log.e(TAG, "Erreur scan sous-consoles", e);
            }
        }).start();
    }
    
    private void setDefaultConsoles() {
        Log.i(TAG, "=== DEBUT SCAN CONSOLES ===");
        availableConsoles = new ArrayList<>();
        
        // Scanner les répertoires localement comme fallback rapide
        new Thread(() -> {
            try {
                com.retroplay.usecases.ScanGamesUseCase scanUseCase = new com.retroplay.usecases.ScanGamesUseCase(this);
                com.retroplay.usecases.ScanGamesUseCase.ScanResult result = scanUseCase.scanConsoles();
                
                if (result.success) {
                    // Convertir ConsoleInfo du UseCase vers ConsoleInfo de l'Activity
                    List<ConsoleInfo> scannedConsoles = new ArrayList<>();
                    for (com.retroplay.usecases.ScanGamesUseCase.ConsoleInfo useCaseConsole : result.consoles) {
                        scannedConsoles.add(new ConsoleInfo(
                            useCaseConsole.id,
                            useCaseConsole.name,
                            useCaseConsole.fullName,
                            useCaseConsole.directory
                        ));
                    }
                    
                    runOnUiThread(() -> {
                        availableConsoles = scannedConsoles;
                        if (availableConsoles.isEmpty()) {
                            // Si aucune console trouvée, ajouter les défauts
                            availableConsoles.add(new ConsoleInfo("nes", "NES", "Nintendo Entertainment System", "nes"));
                            availableConsoles.add(new ConsoleInfo("snes", "SNES", "Super Nintendo Entertainment System", "snes"));
                            availableConsoles.add(new ConsoleInfo("n64", "N64", "Nintendo 64", "n64"));
                        }
                        
                        // Vérifier si la console actuelle existe encore, sinon utiliser la première disponible
                        boolean currentConsoleExists = false;
                        for (ConsoleInfo console : availableConsoles) {
                            if (console.id.equals(currentConsole)) {
                                currentConsoleExists = true;
                                break;
                            }
                        }
                        if (!currentConsoleExists && !availableConsoles.isEmpty()) {
                            currentConsole = availableConsoles.get(0).id;
                            Log.i(TAG, "Console actuelle non trouvée, sélection de: " + currentConsole);
                        }
                        
                        Log.i(TAG, "Consoles disponibles dans dropdown: " + availableConsoles.size());
                        
                        // Sauvegarder dans le cache pour le prochain démarrage
                        saveConsolesToCache(availableConsoles);
                        
                        // Scanner et ajouter les sous-consoles localement
                        scanAndAddSubconsoles();
                        
                        // Charger les jeux maintenant que les consoles sont chargées (premier chargement uniquement)
                        onConsolesLoaded();
                    });
                } else {
                    // Si le scan échoue, utiliser les consoles par défaut
                    runOnUiThread(() -> {
                        availableConsoles.add(new ConsoleInfo("nes", "NES", "Nintendo Entertainment System", "nes"));
                        availableConsoles.add(new ConsoleInfo("snes", "SNES", "Super Nintendo Entertainment System", "snes"));
                        availableConsoles.add(new ConsoleInfo("n64", "N64", "Nintendo 64", "n64"));
                        saveConsolesToCache(availableConsoles);
                        onConsolesLoaded();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur scan consoles", e);
                runOnUiThread(() -> {
                    availableConsoles.add(new ConsoleInfo("nes", "NES", "Nintendo Entertainment System", "nes"));
                    availableConsoles.add(new ConsoleInfo("snes", "SNES", "Super Nintendo Entertainment System", "snes"));
                    availableConsoles.add(new ConsoleInfo("n64", "N64", "Nintendo 64", "n64"));
                    saveConsolesToCache(availableConsoles);
                    
                    // Charger les jeux maintenant que les consoles sont chargées (premier chargement uniquement)
                    onConsolesLoaded();
                });
            }
        }).start();
    }
    
    private String getDefaultDisplayName(String consoleId) {
        com.retroplay.usecases.ScanGamesUseCase scanUseCase = new com.retroplay.usecases.ScanGamesUseCase(this);
        return scanUseCase.getDefaultDisplayName(consoleId);
    }
    
    private String getSubconsoleDisplayName(String parent, String subconsole) {
        com.retroplay.usecases.ScanGamesUseCase scanUseCase = new com.retroplay.usecases.ScanGamesUseCase(this);
        return scanUseCase.getSubconsoleDisplayName(parent, subconsole);
    }
    
    private void updateConsoleTitle() {
        ConsoleInfo currentConsoleInfo = null;
        for (ConsoleInfo console : availableConsoles) {
            if (console.id.equals(currentConsole)) {
                currentConsoleInfo = console;
                break;
            }
        }
        
        if (currentConsoleInfo != null) {
            // Mettre à jour le consoleSelectorButton comme titre principal
            consoleSelectorButton.setText(currentConsoleInfo.name + " LIBRARY ▼");
        } else {
            consoleSelectorButton.setText(currentConsole.toUpperCase() + " LIBRARY ▼");
        }
    }
    
    private void checkAvailableConsoles() {
        java.io.File gamelibraryDir = new java.io.File("/storage/emulated/0/GameLibrary-Data/");
        if (!gamelibraryDir.exists()) {
            Log.w(TAG, "Répertoire gamelibrary n'existe pas: " + gamelibraryDir.getAbsolutePath());
            return;
        }
        
        java.io.File[] consoleDirs = gamelibraryDir.listFiles(java.io.File::isDirectory);
        if (consoleDirs != null) {
            Log.i(TAG, "Consoles disponibles:");
            for (java.io.File dir : consoleDirs) {
                java.io.File gamelist = new java.io.File(dir, "gamelist.json");
                Log.i(TAG, "- " + dir.getName() + ": " + (gamelist.exists() ? "OK" : "MANQUANT"));
            }
        }
    }
    
    private void showGenreFilter() {
        if (filterManager == null) return;
        
        // Get unique genres
        java.util.Set<String> genres = filterManager.getAvailableGenres();
        String[] genreArray = genres.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filtrer par genre")
               .setItems(genreArray, (dialog, which) -> {
                   String selectedGenre = genreArray[which];
                   filterByGenre(selectedGenre);
               });
        builder.show();
    }
    
    private void filterByGenre(String genre) {
        if (filterManager == null) return;
        
        List<Game> filtered = filterManager.applyGenreFilter(genre);
        
        filteredGames.clear();
        filteredGames.addAll(filtered);
        
        filterChip.setText(genre);
        updateGamesCount();
        updateEmptyState();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    private void updateGamesCount() {
        if (gamesCount != null) {
            gamesCount.setText(getCurrentPageGames().size() + " jeux");
        }
        
        // Update pagination
        updatePaginationUI();
    }
    
    private void updateEmptyState() {
        if (emptyState != null) {
            boolean isEmpty = getCurrentPageGames().isEmpty();
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            
            if (isEmpty && emptyStateTitle != null && emptyStateSubtitle != null) {
                // Déterminer le contexte pour personnaliser le message
                String searchText = searchInput != null ? searchInput.getText().toString().trim() : "";
                boolean isSearching = !searchText.isEmpty();
                boolean hasAllGames = !games.isEmpty();
                
                if (isSearching) {
                    // Recherche active
                    emptyStateTitle.setText("Aucun résultat");
                    emptyStateSubtitle.setText("Aucun jeu ne correspond à \"" + searchText + "\"");
                } else if (hasAllGames && filteredGames.isEmpty()) {
                    // Filtre alphabétique actif mais aucun jeu ne correspond
                    String currentLetter = getCurrentLetter();
                    if ("#".equals(currentLetter)) {
                        emptyStateTitle.setText("Aucun jeu numérique");
                        emptyStateSubtitle.setText("Aucun jeu ne commence par un chiffre\nCliquez sur une autre lettre ou cherchez un jeu");
                    } else {
                        emptyStateTitle.setText("Aucun jeu pour '" + currentLetter + "'");
                        emptyStateSubtitle.setText("Aucun jeu ne commence par la lettre " + currentLetter + "\nCliquez sur une autre lettre ou cherchez un jeu");
                    }
                } else {
                    // Vraiment aucun jeu dans la console
                    emptyStateTitle.setText("Aucun jeu trouvé");
                    emptyStateSubtitle.setText("Cette console ne contient aucun jeu\nUtilisez Console Manager pour scanner des ROMs");
                }
            }
        }
    }
    
    private void updatePaginationUI() {
        if (paginationManager == null) return;
        
        com.retroplay.managers.PaginationManager.PaginationInfo info = paginationManager.getPaginationInfo();
        int totalPages = info.totalPages;
        int currentPage = info.currentPage - 1; // Convert to 0-based
        
        if (paginationInfo != null) {
            if (totalPages > 1) {
                paginationInfo.setText("Pages " + info.currentPage + "/" + totalPages);
            } else {
                paginationInfo.setText("Pages 1/1");
            }
        }
        
        // Update button states
        if (paginationPrev != null) {
            paginationPrev.setAlpha(currentPage > 0 ? 1.0f : 0.5f);
            paginationPrev.setClickable(currentPage > 0);
        }
        
        if (paginationNext != null) {
            paginationNext.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.5f);
            paginationNext.setClickable(currentPage < totalPages - 1);
        }
    }
    
    private void goToPreviousPage() {
        if (paginationManager != null) {
            paginationManager.goToPreviousPage();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updatePaginationUI();
            updateGamesCount();
            updateEmptyState();
            recyclerView.smoothScrollToPosition(0);
        }
    }
    
    private void goToNextPage() {
        if (paginationManager != null) {
            paginationManager.goToNextPage();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updatePaginationUI();
            updateGamesCount();
            updateEmptyState();
            recyclerView.smoothScrollToPosition(0);
        }
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Crée les répertoires nécessaires au premier lancement
     */
    private void createRequiredDirectories() {
        String[] directories = {
            "/storage/emulated/0/RetroPlay-Files/sites/gamelibrary",
            "/storage/emulated/0/GameLibrary-Data",
            "/storage/emulated/0/GameLibrary-Data/saves",
            "/storage/emulated/0/GameLibrary-Data/states"
        };
        
        // Créer aussi le répertoire cores dans le répertoire privé de l'app
        File coresDir = new File(getFilesDir(), "cores");
        if (!coresDir.exists()) {
            if (coresDir.mkdirs()) {
                Log.i(TAG, "Created cores directory: " + coresDir.getAbsolutePath());
            }
        }
        
        for (String dirPath : directories) {
            try {
                File dir = new File(dirPath);
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        Log.i(TAG, "Created directory: " + dirPath);
                    } else {
                        Log.w(TAG, "Failed to create directory: " + dirPath);
                    }
                } else {
                    Log.d(TAG, "Directory already exists: " + dirPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating directory " + dirPath + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Copie les fichiers par défaut depuis les assets vers le stockage interne
     * si ils n'existent pas déjà (permet la personnalisation)
     */
    private void copyDefaultHtmlToStorage() {
        new Thread(() -> {
            try {
                String basePath = "/storage/emulated/0/RetroPlay-Files/sites/gamelibrary/";
                String assetsPath = "sites/gamelibrary";
                
                // Lister tous les fichiers dans assets/sites/gamelibrary/
                String[] files = getAssets().list(assetsPath);
                if (files == null || files.length == 0) {
                    Log.w(TAG, "No files found in assets/" + assetsPath);
                    return;
                }
                
                // Vérifier si déjà copié
                File siteDir = new File(basePath);
                if (siteDir.exists() && siteDir.listFiles() != null && siteDir.listFiles().length >= files.length) {
                    Log.i(TAG, "Site files already copied, skipping");
                    return;
                }
                
                final int totalFiles = files.length;
                Log.i(TAG, "Found " + totalFiles + " files to copy from assets");
                
                runOnUiThread(() -> {
                    android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                    progressDialog.setTitle("First Launch Setup");
                    progressDialog.setMessage("Copying site files (0/" + totalFiles + ")...");
                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMax(totalFiles);
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                    
                    new Thread(() -> {
                        int copiedCount = 0;
                        
                        for (int i = 0; i < files.length; i++) {
                            String fileName = files[i];
                            final int currentIndex = i + 1;
                            
                            try {
                                File targetFile = new File(basePath + fileName);
                                
                                // Ne copier que si le fichier n'existe pas déjà
                                if (!targetFile.exists()) {
                                    Log.d(TAG, "Copying " + fileName + " to storage");
                                    
                                    // Lire depuis les assets
                                    InputStream is = getAssets().open(assetsPath + "/" + fileName);
                                    byte[] buffer = new byte[is.available()];
                                    is.read(buffer);
                                    is.close();
                                    
                                    // Écrire vers le stockage
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
                                    fos.write(buffer);
                                    fos.close();
                                    
                                    copiedCount++;
                                }
                                
                                final int finalCopied = copiedCount;
                                runOnUiThread(() -> {
                                    progressDialog.setProgress(currentIndex);
                                    progressDialog.setMessage("Copying site files (" + currentIndex + "/" + totalFiles + ")...\n" + fileName);
                                });
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error copying " + fileName + ": " + e.getMessage());
                            }
                        }
                        
                        final int finalCopiedCount = copiedCount;
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (finalCopiedCount > 0) {
                                Log.i(TAG, "Copied " + finalCopiedCount + " files to RetroPlay-Files/sites/");
                                Toast.makeText(this, 
                                    "Site files ready: " + finalCopiedCount + "/" + totalFiles, 
                                    Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d(TAG, "All site files already exist");
                            }
                        });
                    }).start();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error copying files from assets: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Maintenir le mode plein écran même après perte de focus
            com.retroplay.managers.FullscreenManager.getInstance().setupFullscreenMode(this);
        }
    }
    
    /**
     * Démarre le WebServerService (port 7777) pour EmulatorJS
     * Le service est persistant et reste actif en arrière-plan
     */
    private void startWebServerService() {
        // Vérifier si le service est déjà démarré
        if (isServiceRunning(WebServerService.class)) {
            Log.i(TAG, "WebServerService already running, skipping start");
            return;
        }
        
        try {
            Log.i(TAG, "Starting WebServerService on port 7777...");
            Intent serviceIntent = new Intent(this, WebServerService.class);
            
            // ⭐ FIX Android 12+: Use startForegroundService (required for foreground services)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.i(TAG, "WebServerService started successfully");
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            // ⭐ FIX: Handle Android 12+ restriction - service must be started from visible activity
            Log.e(TAG, "ForegroundServiceStartNotAllowedException: " + e.getMessage());
            Log.w(TAG, "Service cannot start as foreground. Starting as regular service (may be killed by system).");
            try {
                // Try as regular service (will be less reliable but won't crash)
                Intent serviceIntent = new Intent(this, WebServerService.class);
                startService(serviceIntent);
                Toast.makeText(this, "WebServer démarré (mode arrière-plan)", Toast.LENGTH_SHORT).show();
            } catch (Exception e2) {
                Log.e(TAG, "Error starting WebServerService as regular service: ", e2);
                Toast.makeText(this, "Erreur démarrage WebServer: " + e2.getMessage(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting WebServerService: ", e);
            Toast.makeText(this, "Erreur démarrage WebServer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Vérifie si un service est déjà en cours d'exécution
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    Log.i(TAG, "Service " + serviceClass.getSimpleName() + " is already running");
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Install RetroArch overlays from assets to external storage
     * Called at first launch only (async)
     */
    private void installRetroArchOverlays() {
        // Check if already installed first (avoid showing dialog unnecessarily)
        new Thread(() -> {
            java.io.File overlayDir = new java.io.File("/storage/emulated/0/RetroPlay-Data/overlays");
            if (overlayDir.exists() && overlayDir.listFiles() != null && overlayDir.listFiles().length > 0) {
                Log.i(TAG, "Overlays already installed, skipping");
                return;
            }
            
            // Create progress dialog on UI thread
            runOnUiThread(() -> {
                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                progressDialog.setTitle("First Launch Setup");
                progressDialog.setMessage("Installing overlays (0/25)...");
                progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(25);
                progressDialog.setCancelable(false);
                progressDialog.show();
                
                new Thread(() -> {
                    try {
                        com.retroplay.overlay.assets.OverlayAssetManager assetManager = 
                            new com.retroplay.overlay.assets.OverlayAssetManager(this);
                        
                        Log.i(TAG, "Installing RetroArch overlays...");
                        boolean success = assetManager.installOverlaysIfNeeded(new com.retroplay.overlay.assets.OverlayAssetManager.ProgressCallback() {
                            @Override
                            public void onProgress(int current, int total, String packageName) {
                                runOnUiThread(() -> {
                                    progressDialog.setProgress(current);
                                    progressDialog.setMessage("Installing overlays (" + current + "/" + total + ")...\n" + packageName);
                                });
                            }
                            
                            @Override
                            public void onComplete(int successCount, int total) {
                                runOnUiThread(() -> {
                                    progressDialog.dismiss();
                                    if (successCount > 0) {
                                        Toast.makeText(GameListActivity.this, 
                                            "Overlays installed: " + successCount + "/" + total, 
                                            Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                        
                        if (success) {
                            Log.i(TAG, "RetroArch overlays installed successfully");
                        } else {
                            Log.w(TAG, "RetroArch overlays already installed or installation failed");
                            runOnUiThread(() -> progressDialog.dismiss());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error installing RetroArch overlays", e);
                        runOnUiThread(() -> progressDialog.dismiss());
                    }
                }).start();
            });
        }).start();
    }
    
    /**
     * Copie les données EmulatorJS depuis les assets vers le stockage
     * (cores, bios, compression, etc.)
     */
    private void copyEmulatorJSData() {
        new Thread(() -> {
            try {
                // Vérifier si déjà copié via fichier marqueur
                File markerFile = new File("/storage/emulated/0/GameLibrary-Data/.emulatorjs_installed");
                if (markerFile.exists()) {
                    Log.i(TAG, "EmulatorJS data already installed (marker file found), skipping");
                    return;
                }
                
                final int[] currentProgress = {0};
                
                runOnUiThread(() -> {
                    android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                    progressDialog.setTitle("First Launch Setup");
                    progressDialog.setMessage("Copying EmulatorJS data...");
                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMax(100); // Mode pourcentage
                    progressDialog.setProgress(0);
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                    
                    new Thread(() -> {
                        try {
                            Log.i(TAG, "Starting EmulatorJS data copy...");
                            
                            // Callback pour mettre à jour la progression
                            ProgressCallback callback = new ProgressCallback() {
                                @Override
                                public void onFileProgress(String fileName) {
                                    currentProgress[0]++;
                                    if (currentProgress[0] % 50 == 0) {
                                        runOnUiThread(() -> {
                                            progressDialog.setMessage("Copying EmulatorJS...\n" + currentProgress[0] + " files copied\n" + fileName);
                                            // Mise à jour approximative (on ne connaît pas le total)
                                            int progress = Math.min(95, currentProgress[0] / 50);
                                            progressDialog.setProgress(progress);
                                        });
                                    }
                                }
                            };
                            
                            int filesCopied = copyAssetFolderWithProgress("GameLibrary-Data/data", "/storage/emulated/0/GameLibrary-Data/data", callback);
                            
                            // NOTE: Cheats are NOT copied during first launch to speed up installation
                            // Users can manually copy them later if needed from assets/GameLibrary-Data/cheats/
                            Log.i(TAG, "Skipping cheats folder (not essential for first launch)");
                            
                            // Créer le fichier marqueur pour indiquer que l'installation est complète
                            try {
                                markerFile.createNewFile();
                                Log.i(TAG, "Created installation marker file");
                            } catch (Exception e) {
                                Log.w(TAG, "Could not create marker file: " + e.getMessage());
                            }
                            
                            final int totalCopied = filesCopied;
                            runOnUiThread(() -> {
                                progressDialog.setProgress(100);
                                progressDialog.dismiss();
                                if (totalCopied > 0) {
                                    Log.i(TAG, "EmulatorJS data copied: " + totalCopied + " files");
                                    Toast.makeText(this, 
                                        "EmulatorJS ready: " + totalCopied + " files", 
                                        Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error copying EmulatorJS data", e);
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Error copying EmulatorJS data", Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in copyEmulatorJSData", e);
            }
        }).start();
    }
    
    /**
     * Interface de callback pour la progression de copie
     */
    private interface ProgressCallback {
        void onFileProgress(String fileName);
    }
    
    /**
     * Copie récursivement un dossier depuis assets avec callback de progression
     */
    private int copyAssetFolderWithProgress(String assetPath, String destPath, ProgressCallback callback) throws Exception {
        int filesCopied = 0;
        
        String[] files = getAssets().list(assetPath);
        if (files == null || files.length == 0) {
            return 0;
        }
        
        // Créer le répertoire de destination
        File destDir = new File(destPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        for (String fileName : files) {
            String assetFilePath = assetPath + "/" + fileName;
            String destFilePath = destPath + "/" + fileName;
            
            try {
                // Vérifier si c'est un dossier ou un fichier
                String[] subFiles = getAssets().list(assetFilePath);
                if (subFiles != null && subFiles.length > 0) {
                    // C'est un dossier, copie récursive
                    filesCopied += copyAssetFolderWithProgress(assetFilePath, destFilePath, callback);
                } else {
                    // C'est un fichier
                    File destFile = new File(destFilePath);
                    if (!destFile.exists()) {
                        InputStream is = getAssets().open(assetFilePath);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        
                        is.close();
                        fos.close();
                        filesCopied++;
                        
                        // Notifier la progression
                        if (callback != null) {
                            callback.onFileProgress(fileName);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error copying " + assetFilePath + ": " + e.getMessage());
            }
        }
        
        return filesCopied;
    }
    
    /**
     * Vérifie si un répertoire contient des fichiers ROM
     */
    private boolean hasRomFiles(java.io.File dir) {
        com.retroplay.usecases.ScanGamesUseCase scanUseCase = new com.retroplay.usecases.ScanGamesUseCase(this);
        return scanUseCase.hasRomFiles(dir);
    }
    
    // NOTE: Les méthodes de scan ont été déplacées dans SplashActivity
    // pour que le scan se fasse pendant la demande de permissions.
    // Ces méthodes peuvent être supprimées ou gardées pour un scan manuel futur.
}

