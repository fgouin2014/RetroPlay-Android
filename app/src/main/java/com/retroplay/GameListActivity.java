
package com.retroplay;

import android.content.Context;
import android.content.Intent;
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
    private String currentLetter = "#";
    private int currentPage = 0;
    private int gamesPerPage = 20;
    private List<Game> currentPageGames = new ArrayList<>();
    
    // State
    private String currentFilter = "Tous";
    private boolean isLoading = false;
    private boolean searchAllConsoles = false; // false = console actuelle, true = toutes les consoles
    private boolean showOnlyFavorites = false; // false = tous les jeux, true = favoris uniquement

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Mode plein écran - masquer barre d'état et navigation
        setupFullscreenMode();
        
        setContentView(R.layout.activity_game_list);
        
        setupToolbar();
        setupViews();
        setupRecyclerView();
        
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
        ThemeManager themeManager = ThemeManager.getInstance(this);
        int primaryColor = themeManager.getPrimaryColor(this);
        int mediumColor = themeManager.getMediumColor(this);
        int lightColor = themeManager.getLightColor(this);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);
        
        // Appliquer au header (background)
        View headerView = findViewById(R.id.consoleSelectorButton);
        if (headerView != null && headerView.getParent() instanceof ViewGroup) {
            ViewGroup headerParent = (ViewGroup) headerView.getParent();
            headerParent.setBackgroundColor(headerBackgroundColor);
        }
        
        // Console Selector Button (with dynamic drawable)
        if (consoleSelectorButton != null) {
            consoleSelectorButton.setTextColor(primaryColor);
            consoleSelectorButton.setAlpha(1.0f);
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable selectorDrawable = new android.graphics.drawable.GradientDrawable();
            selectorDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            selectorDrawable.setCornerRadius(6 * getResources().getDisplayMetrics().density); // 6dp
            selectorDrawable.setColor(headerBackgroundColor);
            selectorDrawable.setStroke((int)(1 * getResources().getDisplayMetrics().density), primaryColor); // 1dp stroke with theme color
            consoleSelectorButton.setBackground(selectorDrawable);
        }
        
        // Console Config Button
        if (consoleConfigButton != null) {
            consoleConfigButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
            consoleConfigButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
            consoleConfigButton.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
            consoleConfigButton.setAlpha(1.0f);
        }
        
        // Console Manager Button
        if (consoleManagerButton != null) {
            consoleManagerButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
            consoleManagerButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
            consoleManagerButton.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
            consoleManagerButton.setAlpha(1.0f);
        }
        
        // Favorites Button
        if (favoritesButton != null) {
            favoritesButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
            favoritesButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
            favoritesButton.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
            favoritesButton.setAlpha(1.0f);
        }
        
        // Search Toggle Button
        if (searchToggleButton != null) {
            searchToggleButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
            searchToggleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
            searchToggleButton.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
            searchToggleButton.setAlpha(1.0f);
        }
        
        // Search Input
        if (searchInput != null) {
            searchInput.setTextColor(primaryColor);
            searchInput.setHintTextColor(lightColor);
            searchInput.setAlpha(1.0f);
        }
        
        // Search Scope Toggle
        updateSearchScopeButton();
        
        // Games Count (use text secondary for better contrast)
        if (gamesCount != null) {
            gamesCount.setTextColor(themeManager.getTextSecondaryColor(this));
            gamesCount.setAlpha(1.0f);
        }
        
        // Filter Chip (with dynamic drawable)
        if (filterChip != null) {
            filterChip.setTextColor(primaryColor);
            filterChip.setAlpha(1.0f);
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable chipDrawable = new android.graphics.drawable.GradientDrawable();
            chipDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            chipDrawable.setCornerRadius(6 * getResources().getDisplayMetrics().density); // 6dp
            chipDrawable.setColor(headerBackgroundColor);
            chipDrawable.setStroke((int)(1 * getResources().getDisplayMetrics().density), primaryColor); // 1dp stroke with theme color
            filterChip.setBackground(chipDrawable);
        }
        
        // Appliquer au bouton de filtre favoris
        if (favoritesFilterButton != null) {
            favoritesFilterButton.setAlpha(1.0f);
            if (showOnlyFavorites) {
                favoritesFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                favoritesFilterButton.setIconTintResource(R.color.kitt_black);
            } else {
                favoritesFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
                int primaryColorResId = themeManager.getPrimaryColorResId(this);
                if (primaryColorResId != 0) {
                    favoritesFilterButton.setIconTintResource(primaryColorResId);
                } else {
                    favoritesFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
                }
            }
        }
        
        // Loading Progress
        if (loadingProgress != null) {
            loadingProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(primaryColor));
        }
        
        // Empty State (use text primary for better contrast)
        if (emptyStateTitle != null) {
            emptyStateTitle.setTextColor(themeManager.getTextPrimaryColor(this));
            emptyStateTitle.setAlpha(1.0f);
        }
        if (emptyStateSubtitle != null) {
            emptyStateSubtitle.setTextColor(themeManager.getTextSecondaryColor(this));
            emptyStateSubtitle.setAlpha(1.0f);
        }
        
        // FAB Random
        if (fabRandom != null) {
            fabRandom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            fabRandom.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.kitt_black)));
            fabRandom.setAlpha(1.0f);
        }
        
        // Pagination Footer (background)
        View paginationFooter = findViewById(R.id.paginationFooter);
        if (paginationFooter != null) {
            paginationFooter.setBackgroundColor(headerBackgroundColor);
        }
        
        // Pagination Buttons (with dynamic drawables)
        float density = getResources().getDisplayMetrics().density;
        if (paginationPrev != null) {
            paginationPrev.setTextColor(primaryColor);
            paginationPrev.setAlpha(1.0f);
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable prevDrawable = new android.graphics.drawable.GradientDrawable();
            prevDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            prevDrawable.setCornerRadius(6 * density); // 6dp
            prevDrawable.setColor(headerBackgroundColor);
            prevDrawable.setStroke((int)(1 * density), primaryColor); // 1dp stroke with theme color
            paginationPrev.setBackground(prevDrawable);
        }
        if (paginationInfo != null) {
            paginationInfo.setTextColor(primaryColor);
            paginationInfo.setAlpha(1.0f);
        }
        if (paginationNext != null) {
            paginationNext.setTextColor(primaryColor);
            paginationNext.setAlpha(1.0f);
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable nextDrawable = new android.graphics.drawable.GradientDrawable();
            nextDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            nextDrawable.setCornerRadius(6 * density); // 6dp
            nextDrawable.setColor(headerBackgroundColor);
            nextDrawable.setStroke((int)(1 * density), primaryColor); // 1dp stroke with theme color
            paginationNext.setBackground(nextDrawable);
        }
        
        // Appliquer aux boutons alphabétiques
        updateAlphabetAvailability();
    }
    
    private void setupFullscreenMode() {
        // Masquer la barre d'état et la navigation (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            androidx.core.view.WindowInsetsControllerCompat controller = 
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            // Fallback pour API < 30
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
        
        // Garder l'écran allumé
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                filterGames(s.toString());
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
               paginationPrev.setOnClickListener(v -> goToPreviousPage());
               paginationNext.setOnClickListener(v -> goToNextPage());
               
               // Setup random game button
               fabRandom.setOnClickListener(v -> selectRandomGame());
               
               // Setup filter chip
               filterChip.setOnClickListener(v -> showGenreFilter());
               
               // Setup favorites filter button
               favoritesFilterButton.setOnClickListener(v -> toggleFavoritesFilter());
               
               // Setup alphabetical pagination
               setupAlphabetPagination();
    }

    private void showOverflowMenuSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        
        // Appliquer le thème au BottomSheetDialog
        ThemeManager themeManager = ThemeManager.getInstance(this);
        int primaryColor = themeManager.getPrimaryColor(this);
        int backgroundColor = getResources().getColor(R.color.kitt_black);
        int textColor = primaryColor;

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(backgroundColor);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(backgroundColor);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(pad, pad, pad, pad);

        // Helper to add a button
        java.util.function.BiConsumer<String, Runnable> addItem = (label, action) -> {
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(label);
            btn.setTextColor(primaryColor);
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btn.setRippleColor(android.content.res.ColorStateList.valueOf(primaryColor));
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.topMargin = pad / 2;
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                sheet.dismiss();
                action.run();
            });
            container.addView(btn);
        };

        addItem.accept("Open Gallery (Console)", this::openConsoleGallery);
        addItem.accept("Favorites", this::openFavorites);
        addItem.accept("Console Manager", this::openConsoleManager);
        addItem.accept("Console Config", this::openConsoleConfig);
        addItem.accept("Random Game", this::selectRandomGame);
        addItem.accept("Pagination…", this::showPagination);
        addItem.accept(searchAllConsoles ? "Search Scope: ALL (tap to switch)" : "Search Scope: CURRENT (tap to switch)", () -> {
            toggleSearchScope();
        });
        addItem.accept("Theme: " + ThemeManager.getInstance(this).getCurrentTheme().getDisplayName() + " (tap to change)", this::showThemeSelector);
        
        // Option pour servir la bibliothèque sur le réseau
        WebServerPreferences webServerPrefs = WebServerPreferences.getInstance(this);
        boolean networkMode = webServerPrefs.isNetworkServerEnabled();
        addItem.accept("Network Server: " + (networkMode ? "ON (tap to disable)" : "OFF (tap to enable)"), () -> {
            toggleNetworkServerMode();
        });

        scrollView.addView(container, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        sheet.setContentView(scrollView);
        
        // Appliquer le thème au fond du BottomSheetDialog
        android.view.View bottomSheet = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(backgroundColor);
        }
        
        sheet.show();
    }
    
    /**
     * Active/désactive le mode serveur réseau
     * Quand activé, le serveur reste actif même après fermeture des jeux WASM
     */
    private void toggleNetworkServerMode() {
        WebServerPreferences prefs = WebServerPreferences.getInstance(this);
        boolean newState = !prefs.isNetworkServerEnabled();
        prefs.setNetworkServerEnabled(newState);
        
        if (newState) {
            // Démarrer le serveur si activé
            startWebServerService();
            Toast.makeText(this, 
                "Network Server: ON\nAccess: http://" + getLocalIpAddress() + ":7777/", 
                Toast.LENGTH_LONG).show();
        } else {
            // Arrêter le serveur si désactivé (sauf si un jeu WASM est actif)
            // On laisse WebViewActivity gérer l'arrêt quand elle se ferme
            Toast.makeText(this, "Network Server: OFF\nServer will stop when WASM games close", 
                Toast.LENGTH_SHORT).show();
        }
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
                Log.i(TAG, "Chargement de la liste des jeux pour " + currentConsole.toUpperCase() + "...");
                
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
                
                // Lire le gamelist.json depuis le stockage interne selon la console sélectionnée
                // Support des sous-consoles (ex: fbneo/sega)
                // Utiliser le nom réel du répertoire, pas l'ID canonique
                String gamelistPath = "/storage/emulated/0/GameLibrary-Data/" + realConsoleDirectory + "/gamelist.json";
                java.io.File gamelistFile = new java.io.File(gamelistPath);
                Log.d(TAG, "Looking for gamelist.json at: " + gamelistPath);
                
                if (!gamelistFile.exists()) {
                    Log.i(TAG, "Fichier gamelist.json non trouvé: " + gamelistPath);
                    Log.i(TAG, "Tentative AUTO SCAN via API serveur...");
                    
                    // Try to load auto-generated gamelist from server
                    // Utiliser le nom réel du répertoire pour la requête HTTP
                    try {
                        java.net.URL autoScanUrl = new java.net.URL("http://localhost:7777/gamedata/" + realConsoleDirectory + "/gamelist.json");
                        java.net.HttpURLConnection autoConn = (java.net.HttpURLConnection) autoScanUrl.openConnection();
                        autoConn.setRequestMethod("GET");
                        autoConn.setConnectTimeout(5000);
                        autoConn.setReadTimeout(5000);
                        
                        if (autoConn.getResponseCode() == 200) {
                            Log.i(TAG, "AUTO SCAN successful for " + currentConsole);
                            // Read the auto-generated gamelist
                            java.io.BufferedReader autoReader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(autoConn.getInputStream()));
                            StringBuilder autoResponse = new StringBuilder();
                            String autoLine;
                            while ((autoLine = autoReader.readLine()) != null) {
                                autoResponse.append(autoLine);
                            }
                            autoReader.close();
                            
                            // Parse the auto-generated JSON - Passer realConsoleDirectory pour les chemins d'images
                            parseAndDisplayGames(autoResponse.toString(), realConsoleDirectory);
                            return;
                        } else {
                            Log.w(TAG, "AUTO SCAN failed with code: " + autoConn.getResponseCode());
                        }
                    } catch (Exception autoEx) {
                        Log.e(TAG, "AUTO SCAN error: " + autoEx.getMessage());
                    }
                    
                    // If auto-scan also failed, show error
                    runOnUiThread(() -> {
                        isLoading = false;
                        loadingProgress.setVisibility(View.GONE);
                        emptyState.setVisibility(View.VISIBLE);
                        android.widget.Toast.makeText(GameListActivity.this, 
                            "No games found for " + currentConsole.toUpperCase() + " (no gamelist.json and auto-scan failed)", 
                            android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                // Lire le contenu du fichier
                java.io.FileInputStream fis = new java.io.FileInputStream(gamelistFile);
                byte[] buffer = new byte[(int) gamelistFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer, StandardCharsets.UTF_8);

                // Parse and display games - Passer realConsoleDirectory pour les chemins d'images
                parseAndDisplayGames(json, realConsoleDirectory);

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
    
    private void parseAndDisplayGames(String jsonString, String realConsoleDirectory) {
        try {
            // Parse JSON object format: {"games": [...]}
            jsonString = jsonString.trim();
            JSONObject jsonObj = new JSONObject(jsonString);
            JSONArray arr = jsonObj.getJSONArray("games");
            
            // Utiliser realConsoleDirectory pour les chemins d'images (nom réel du répertoire)
            // Utiliser currentConsole pour la configuration (ID canonique)
            String consoleForImages = realConsoleDirectory != null ? realConsoleDirectory : currentConsole;
            Log.d(TAG, "parseAndDisplayGames: Using consoleForImages=" + consoleForImages + " (realConsoleDirectory=" + realConsoleDirectory + ", currentConsole=" + currentConsole + ")");
            
            List<Game> tempGames = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                // Support format ES: "desc"/"releasedate" (nouveau) et "description"/"releaseDate" (legacy)
                String desc = obj.optString("desc", null);
                if (desc == null || desc.isEmpty()) {
                    desc = obj.optString("description", "");  // Fallback legacy
                }
                String releaseDate = obj.optString("releasedate", null);
                if (releaseDate == null || releaseDate.isEmpty()) {
                    releaseDate = obj.optString("releaseDate", "");  // Fallback legacy
                }
                
                // Parser les nouveaux champs ES
                String developer = obj.optString("developer", null);
                String publisher = obj.optString("publisher", null);
                String rating = obj.optString("rating", null);
                String hash = obj.optString("hash", null);
                
                Game game = new Game(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("path"),
                        desc,
                        releaseDate,
                        obj.optString("genre", ""),
                        obj.optString("players", ""),
                        developer,
                        publisher,
                        rating,
                        hash
                );
                // Définir la console pour ce jeu - Utiliser realConsoleDirectory pour les chemins d'images
                // IMPORTANT: Utiliser le nom réel du répertoire (ex: "megadrive") et non l'ID canonique (ex: "genesis")
                // pour que les chemins d'images pointent vers le bon répertoire
                game.setConsole(consoleForImages);
                // Initialiser les chemins vers les images
                game.initializePaths(null);
                tempGames.add(game);
            }

            // Sort games alphabetically
            tempGames.sort((g1, g2) -> g1.getName().compareToIgnoreCase(g2.getName()));
            
            runOnUiThread(() -> {
                games = tempGames;
                
                // Mettre à jour l'état favori de chaque jeu
                updateFavoriteStates(games);
                
                // Auto-selectionner la premiere lettre disponible
                autoSelectFirstAvailableLetter();
                
                adapter = new GameAdapter(currentPageGames, this);
                adapter.setFavoritesManager(favoritesManager);
                recyclerView.setAdapter(adapter);
                
                updateGamesCount();
                loadingProgress.setVisibility(View.GONE);
                isLoading = false;
                
                Log.i(TAG, "Liste des jeux chargée: " + games.size() + " jeux (console: " + currentConsole + ")");
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing games JSON", e);
            runOnUiThread(() -> {
                loadingProgress.setVisibility(View.GONE);
                isLoading = false;
                showError("Error parsing games list");
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
     * Obtient la liste des jeux favoris
     */
    private List<Game> getFavoriteGames() {
        List<Game> favorites = new ArrayList<>();
        if (favoritesManager != null) {
            for (Game game : games) {
                if (favoritesManager.isFavorite(game)) {
                    favorites.add(game);
                }
            }
        }
        return favorites;
    }
    
    /**
     * Toggle le filtre des favoris
     */
    private void toggleFavoritesFilter() {
        showOnlyFavorites = !showOnlyFavorites;
        
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
        filterByLetter(currentLetter);
    }
    
    private void filterGames(String query) {
        if (query.isEmpty()) {
            // Retour à la pagination alphabétique
            filterByLetter(currentLetter);
            return;
        }
        
        if (searchAllConsoles) {
            // Recherche dans TOUTES les consoles
            searchInAllConsoles(query);
        } else {
            // Recherche dans la console actuelle seulement
            filteredGames.clear();
            for (Game game : games) {
                if (game.getName().toLowerCase().contains(query.toLowerCase())) {
                    filteredGames.add(game);
                }
            }
            
            currentPageGames.clear();
            currentPageGames.addAll(filteredGames);
            
            updateGamesCount();
            updateEmptyState();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }
    
    private void searchInAllConsoles(String query) {
        if (isLoading) return;
        
        isLoading = true;
        loadingProgress.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                List<Game> allGames = new ArrayList<>();
                
                // Chercher dans toutes les consoles disponibles
                for (ConsoleInfo console : availableConsoles) {
                    try {
                        String urlStr = "http://localhost:7777/gamedata/" + console.id + "/gamelist.json";
                        java.net.URL url = new java.net.URL(urlStr);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(2000);
                        conn.setReadTimeout(2000);
                        
                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                            
                            // Parser et filtrer les jeux
                            JSONObject jsonObj = new JSONObject(response.toString());
                            JSONArray arr = jsonObj.getJSONArray("games");
                            
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                String gameName = obj.getString("name");
                                
                                // Filtrer par requête
                                if (gameName.toLowerCase().contains(query.toLowerCase())) {
                                    // Utiliser optString() avec valeurs par défaut pour éviter les erreurs
                                    // Support des deux formats : "desc"/"releasedate" (legacy) et "description"/"releaseDate" (nouveau)
                                    // Support format ES: "desc"/"releasedate" (nouveau) et "description"/"releaseDate" (legacy)
                                    String desc = obj.optString("desc", null);
                                    if (desc == null || desc.isEmpty()) {
                                        desc = obj.optString("description", "");
                                    }
                                    String releaseDate = obj.optString("releasedate", null);
                                    if (releaseDate == null || releaseDate.isEmpty()) {
                                        releaseDate = obj.optString("releaseDate", "");
                                    }
                                    
                                    // Parser les nouveaux champs ES
                                    String developer = obj.optString("developer", null);
                                    String publisher = obj.optString("publisher", null);
                                    String rating = obj.optString("rating", null);
                                    String hash = obj.optString("hash", null);
                                    
                                    Game game = new Game(
                                        obj.getString("id"),
                                        gameName,
                                        obj.getString("path"),
                                        desc,
                                        releaseDate,
                                        obj.optString("genre", ""),
                                        obj.optString("players", ""),
                                        developer,
                                        publisher,
                                        rating,
                                        hash
                                    );
                                    game.setConsole(console.id);
                                    game.initializePaths(null);
                                    allGames.add(game);
                                }
                            }
                        }
                        conn.disconnect();
                    } catch (Exception e) {
                        // Console inaccessible ou vide, continuer
                        Log.d(TAG, "Cannot search in console " + console.id + ": " + e.getMessage());
                    }
                }
                
                // Trier les résultats
                allGames.sort((g1, g2) -> g1.getName().compareToIgnoreCase(g2.getName()));
                
                runOnUiThread(() -> {
                    filteredGames.clear();
                    filteredGames.addAll(allGames);
                    
                    currentPageGames.clear();
                    currentPageGames.addAll(filteredGames);
                    
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    
                    updateGamesCount();
                    updateEmptyState();
                    loadingProgress.setVisibility(View.GONE);
                    isLoading = false;
                    
                    Log.i(TAG, "Global search for '" + query + "': " + allGames.size() + " results");
                });
                
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
        if (searchAllConsoles) {
            searchInput.setHint("Rechercher dans TOUTES les consoles...");
        } else {
            String consoleName = currentConsole.toUpperCase();
            searchInput.setHint("Rechercher dans " + consoleName + "...");
        }
    }
    
    private void toggleSearchScope() {
        searchAllConsoles = !searchAllConsoles;
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
    
    private void setupAlphabetPagination() {
        String[] row1 = {"#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"};
        String[] row2 = {"L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V"};
        String[] row3 = {"W", "X", "Y", "Z"};
        
        setupAlphabetRow(row1, alphabetRow1);
        setupAlphabetRow(row2, alphabetRow2);
        setupAlphabetRow(row3, alphabetRow3);
        
        // Mettre à jour les états initiaux
        updateAlphabetAvailability();
    }
    
    private void setupAlphabetRow(String[] letters, LinearLayout row) {
        for (String letter : letters) {
            TextView button = new TextView(this);
            button.setText(letter);
            button.setTextSize(12);
            button.setPadding(8, 8, 8, 8);
            button.setMinWidth(40);
            button.setMinHeight(40);
            button.setGravity(android.view.Gravity.CENTER);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
            );
            params.setMargins(2, 2, 2, 2);
            button.setLayoutParams(params);
            
            ThemeManager themeManager = ThemeManager.getInstance(this);
            ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
            int primaryColor = themeManager.getPrimaryColor(this);
            int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);
            boolean isKittRed = currentTheme == ThemeManager.Theme.KITT_RED;
            
            button.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            button.setClickable(true);
            button.setFocusable(true);
            
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(6 * getResources().getDisplayMetrics().density); // 6dp
            if ("#".equals(letter)) {
                drawable.setColor(primaryColor);
                // Texte noir quand sélectionné (#) - tous les thèmes
                button.setTextColor(getResources().getColor(R.color.kitt_black));
            } else {
                drawable.setColor(headerBackgroundColor);
                // Texte de la couleur primaire du thème par défaut (rouge pour KITT-Red, ambre pour Amber, vert pour Matrix)
                button.setTextColor(primaryColor);
            }
            drawable.setStroke((int)(1 * getResources().getDisplayMetrics().density), primaryColor); // 1dp stroke
            button.setBackground(drawable);
            
            button.setOnClickListener(v -> filterByLetter(letter));
            row.addView(button);
        }
    }
    
    /**
     * Compter combien de jeux commencent par chaque lettre
     */
    private java.util.Map<String, Integer> countGamesByLetter() {
        java.util.Map<String, Integer> letterCounts = new java.util.HashMap<>();
        
        // Initialiser toutes les lettres à 0
        letterCounts.put("#", 0);
        for (char c = 'A'; c <= 'Z'; c++) {
            letterCounts.put(String.valueOf(c), 0);
        }
        
        // Obtenir la liste de base (tous les jeux ou favoris uniquement)
        List<Game> baseList = showOnlyFavorites ? getFavoriteGames() : games;
        
        // Compter les jeux
        for (Game game : baseList) {
            if (game.getName().length() > 0) {
                String firstChar = game.getName().substring(0, 1).toUpperCase();
                if (Character.isDigit(firstChar.charAt(0))) {
                    letterCounts.put("#", letterCounts.get("#") + 1);
                } else if (Character.isLetter(firstChar.charAt(0))) {
                    letterCounts.put(firstChar, letterCounts.getOrDefault(firstChar, 0) + 1);
                }
            }
        }
        
        return letterCounts;
    }
    
    /**
     * Mettre à jour la disponibilité des boutons alphabétiques
     */
    private void updateAlphabetAvailability() {
        java.util.Map<String, Integer> letterCounts = countGamesByLetter();
        
        updateAlphabetRowAvailability(alphabetRow1, letterCounts);
        updateAlphabetRowAvailability(alphabetRow2, letterCounts);
        updateAlphabetRowAvailability(alphabetRow3, letterCounts);
    }
    
    /**
     * Mettre à jour la disponibilité des boutons dans une rangée
     */
    private void updateAlphabetRowAvailability(LinearLayout row, java.util.Map<String, Integer> letterCounts) {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
        int primaryColor = themeManager.getPrimaryColor(this);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(this);
        int blackColor = getResources().getColor(R.color.kitt_black);
        int whiteColor = getResources().getColor(android.R.color.white);
        float density = getResources().getDisplayMetrics().density;
        
        // Special behavior for KITT-Red theme only
        boolean isKittRed = currentTheme == ThemeManager.Theme.KITT_RED;
        
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView button = (TextView) row.getChildAt(i);
            String letter = button.getText().toString();
            int count = letterCounts.getOrDefault(letter, 0);
            boolean hasGames = count > 0;
            
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(6 * density); // 6dp
            drawable.setStroke((int)(1 * density), primaryColor); // 1dp stroke with theme color
            
            if (hasGames) {
                // Lettre avec jeux - active et opaque
                button.setClickable(true);
                button.setAlpha(1.0f);
                
                // Si c'est la lettre sélectionnée
                if (letter.equals(currentLetter)) {
                    drawable.setColor(primaryColor);
                    button.setBackground(drawable);
                    // Texte noir quand sélectionné (tous les thèmes)
                    button.setTextColor(blackColor);
                } else {
                    drawable.setColor(headerBackgroundColor);
                    button.setBackground(drawable);
                    // Texte de la couleur primaire du thème par défaut (rouge pour KITT-Red, ambre pour Amber, vert pour Matrix)
                    button.setTextColor(primaryColor);
                }
            } else {
                // Lettre sans jeux - désactivée et transparente (garde les mêmes couleurs)
                button.setClickable(false);
                button.setAlpha(0.25f);
                
                // Garder les couleurs normales (non sélectionnées)
                drawable.setColor(headerBackgroundColor);
                button.setBackground(drawable);
                button.setTextColor(primaryColor);
            }
        }
    }
    
    private void filterByLetter(String letter) {
        currentLetter = letter;
        currentPage = 0;
        
        Log.i(TAG, "filterByLetter: letter='" + letter + "' total games=" + games.size());
        
        // Obtenir la liste de base (tous les jeux ou favoris uniquement)
        List<Game> baseList = showOnlyFavorites ? getFavoriteGames() : games;
        
        filteredGames.clear();
        if ("#".equals(letter)) {
            // Show games starting with numbers
            for (Game game : baseList) {
                String firstChar = game.getName().substring(0, 1).toUpperCase();
                if (Character.isDigit(firstChar.charAt(0))) {
                    filteredGames.add(game);
                }
            }
        } else {
            // Show games starting with the selected letter
            for (Game game : baseList) {
                String firstChar = game.getName().substring(0, 1).toUpperCase();
                if (firstChar.equals(letter)) {
                    filteredGames.add(game);
                }
            }
        }
        
        Log.i(TAG, "filterByLetter: filteredGames.size=" + filteredGames.size() + " games matched letter '" + letter + "'");
        
        // Pagination
        updateCurrentPage();
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        // Update alphabet button states avec la nouvelle logique
        updateAlphabetAvailability();
        updateGamesCount();
        updateEmptyState();
    }
    
    private void autoSelectFirstAvailableLetter() {
        // Au chargement, essayer d'abord les jeux commencant par des chiffres
        boolean hasNumberGames = false;
        for (Game game : games) {
            String firstChar = game.getName().substring(0, 1).toUpperCase();
            if (Character.isDigit(firstChar.charAt(0))) {
                hasNumberGames = true;
                break;
            }
        }
        
        if (hasNumberGames) {
            Log.d(TAG, "Auto-selection: Found games starting with numbers, selecting '#'");
            filterByLetter("#");
            return;
        }
        
        // Si aucun jeu avec des chiffres, parcourir A-Z pour trouver la premiere lettre disponible
        String[] letters = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", 
                           "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        
        for (String letter : letters) {
            boolean hasGames = false;
            for (Game game : games) {
                String firstChar = game.getName().substring(0, 1).toUpperCase();
                if (firstChar.equals(letter)) {
                    hasGames = true;
                    break;
                }
            }
            
            if (hasGames) {
                Log.d(TAG, "Auto-selection: First letter found with games is '" + letter + "'");
                filterByLetter(letter);
                return;
            }
        }
        
        // Si vraiment aucun jeu
        Log.d(TAG, "Auto-selection: No games found at all");
        filteredGames.clear();
        currentPage = 0;
        updateCurrentPage();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateAlphabetAvailability();
        updateGamesCount();
        updateEmptyState();
    }
    
    private void updateCurrentPage() {
        currentPageGames.clear();
        int startIndex = currentPage * gamesPerPage;
        int endIndex = Math.min(startIndex + gamesPerPage, filteredGames.size());
        
        Log.d(TAG, "updateCurrentPage: page=" + currentPage + " gamesPerPage=" + gamesPerPage + 
                   " filteredGames.size=" + filteredGames.size() + " startIndex=" + startIndex + " endIndex=" + endIndex);
        
        for (int i = startIndex; i < endIndex; i++) {
            currentPageGames.add(filteredGames.get(i));
        }
        
        Log.d(TAG, "updateCurrentPage: currentPageGames.size=" + currentPageGames.size() + " games added");
    }
    
    private void showPagination() {
        if (filteredGames.size() <= gamesPerPage) return;
        
        int totalPages = (int) Math.ceil((double) filteredGames.size() / gamesPerPage);
        String[] pages = new String[totalPages];
        for (int i = 0; i < totalPages; i++) {
            pages[i] = "Page " + (i + 1) + " (" + (i * gamesPerPage + 1) + "-" + Math.min((i + 1) * gamesPerPage, filteredGames.size()) + ")";
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pagination - " + currentLetter)
               .setItems(pages, (dialog, which) -> {
                   currentPage = which;
                   updateCurrentPage();
                   if (adapter != null) {
                       adapter.notifyDataSetChanged();
                   }
                   updateGamesCount();
               });
        builder.show();
    }
    
    private void selectRandomGame() {
        if (currentPageGames.isEmpty()) {
            showError("Aucun jeu disponible");
            return;
        }
        
        int randomIndex = (int) (Math.random() * currentPageGames.size());
        Game randomGame = currentPageGames.get(randomIndex);
        
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
                    currentPageGames.clear();
                    loadGames();
                }
            }
            
            // Recharger les consoles disponibles
            loadAvailableConsoles();
            
            // Recharger les jeux de la console actuelle si pas déjà fait
            if (data == null || !data.getBooleanExtra("gamelistGenerated", false)) {
                games.clear();
                filteredGames.clear();
                currentPageGames.clear();
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
        
        // Reset pagination
        currentLetter = "#";
        currentPage = 0;
        
        // Clear current data
        games.clear();
        filteredGames.clear();
        currentPageGames.clear();
        
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
        String cachedConsolesJson = consolePrefs.getString("consoles_cache", null);
        long cacheTimestamp = consolePrefs.getLong("consoles_cache_timestamp", 0);
        long cacheAge = System.currentTimeMillis() - cacheTimestamp;
        long cacheMaxAge = 5 * 60 * 1000; // 5 minutes
        
        if (cachedConsolesJson != null && cacheAge < cacheMaxAge) {
            Log.i(TAG, "Loading consoles from cache (age: " + (cacheAge / 1000) + "s)");
            try {
                parseConsolesFromCache(cachedConsolesJson);
                // Charger les jeux immédiatement avec le cache
                onConsolesLoaded();
                // Rescanner en arrière-plan pour mettre à jour le cache
                new Thread(() -> {
                    setDefaultConsoles();
                }).start();
                return;
            } catch (Exception e) {
                Log.w(TAG, "Error loading consoles from cache, falling back to scan", e);
            }
        }
        
        // Pas de cache valide, scanner directement
        Log.i(TAG, "No valid cache, scanning consoles directly (optimized - no WebServer dependency)");
        setDefaultConsoles();
    }
    
    private void parseConsolesFromCache(String jsonString) {
        try {
            JSONObject jsonObj = new JSONObject(jsonString);
            JSONArray consolesArray = jsonObj.getJSONArray("consoles");
            
            List<ConsoleInfo> tempConsoles = new ArrayList<>();
            for (int i = 0; i < consolesArray.length(); i++) {
                JSONObject consoleObj = consolesArray.getJSONObject(i);
                tempConsoles.add(new ConsoleInfo(
                    consoleObj.getString("id"),
                    consoleObj.getString("name"),
                    consoleObj.getString("fullName"),
                    consoleObj.getString("directory")
                ));
            }
            
            availableConsoles = tempConsoles;
            Log.i(TAG, "Loaded " + availableConsoles.size() + " consoles from cache");
            
            // Scanner les sous-consoles en arrière-plan
            scanAndAddSubconsoles();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing consoles from cache", e);
            throw new RuntimeException(e);
        }
    }
    
    private void saveConsolesToCache(List<ConsoleInfo> consoles) {
        try {
            JSONObject jsonObj = new JSONObject();
            JSONArray consolesArray = new JSONArray();
            
            for (ConsoleInfo console : consoles) {
                JSONObject consoleObj = new JSONObject();
                consoleObj.put("id", console.id);
                consoleObj.put("name", console.name);
                consoleObj.put("fullName", console.fullName);
                consoleObj.put("directory", console.directory);
                consolesArray.put(consoleObj);
            }
            
            jsonObj.put("consoles", consolesArray);
            String jsonString = jsonObj.toString();
            
            consolePrefs.edit()
                .putString("consoles_cache", jsonString)
                .putLong("consoles_cache_timestamp", System.currentTimeMillis())
                .apply();
            
            Log.i(TAG, "Saved " + consoles.size() + " consoles to cache");
        } catch (Exception e) {
            Log.e(TAG, "Error saving consoles to cache", e);
        }
    }
    
    private void scanAndAddSubconsoles() {
        // Scanner les sous-consoles localement et les ajouter à availableConsoles
        new Thread(() -> {
            try {
                java.io.File gamelibraryDir = new java.io.File("/storage/emulated/0/GameLibrary-Data/");
                if (gamelibraryDir.exists() && gamelibraryDir.isDirectory()) {
                    java.io.File[] directories = gamelibraryDir.listFiles(java.io.File::isDirectory);
                    
                    List<ConsoleInfo> subconsoles = new ArrayList<>();
                    
                    if (directories != null) {
                        for (java.io.File dir : directories) {
                            String dirName = dir.getName();
                            
                            // Ignorer les répertoires système
                            if (dirName.equals("data") || dirName.equals("emulatorjs") || 
                                dirName.equals("vmnes") || dirName.equals("playlists") ||
                                dirName.equals("saves") || dirName.equals(".cache") ||
                                dirName.equals("cheats") || dirName.equals("media")) {
                                continue;
                            }
                            
                            // Scanner les sous-répertoires (ex: fbneo/sega)
                            java.io.File[] subdirectories = dir.listFiles(java.io.File::isDirectory);
                            if (subdirectories != null) {
                                for (java.io.File subdir : subdirectories) {
                                    String subdirName = subdir.getName();
                                    
                                    // Ignorer les dossiers système et les vestiges EmulationStation
                                    if (subdirName.equals("media") || subdirName.equals("saves") || 
                                        subdirName.equals("data") || subdirName.equals(".cache") ||
                                        subdirName.equals("cfg") || subdirName.equals("hi") || 
                                        subdirName.equals("inp") || subdirName.equals("ctrlr") || 
                                        subdirName.equals("diff") || subdirName.equals("comment") ||
                                        subdirName.equals("mame2003") || subdirName.equals("mame2003-plus") ||
                                        subdirName.equals("mame2010") || subdirName.equals("mame2014") ||
                                        subdirName.equals("fbneo") || subdirName.equals("fba") ||
                                        subdirName.equals("kinst")) {  // Organisateur de fichiers CHD
                                        continue;
                                    }
                                    
                                    // Vérifier s'il y a des ROMs dans ce sous-dossier (.zip, .bin, .iso, etc.)
                                    java.io.File[] files = subdir.listFiles();
                                    boolean hasRoms = false;
                                    if (files != null) {
                                        for (java.io.File file : files) {
                                            String fileName = file.getName().toLowerCase();
                                            if (fileName.endsWith(".zip") || fileName.endsWith(".bin") || 
                                                fileName.endsWith(".iso") || fileName.endsWith(".cue") ||
                                                fileName.endsWith(".chd") || fileName.endsWith(".nes") ||
                                                fileName.endsWith(".sfc") || fileName.endsWith(".smc") ||
                                                fileName.endsWith(".n64") || fileName.endsWith(".z64") ||
                                                fileName.endsWith(".gba") || fileName.endsWith(".gbc") ||
                                                fileName.endsWith(".gb") || fileName.endsWith(".md") ||
                                                fileName.endsWith(".sms") || fileName.endsWith(".gg")) {
                                                hasRoms = true;
                                                break;
                                            }
                                        }
                                    }
                                    
                                    if (hasRoms) {
                                        String subConsolePath = dirName + "/" + subdirName;
                                        String subDisplayName = dirName.toUpperCase() + " - " + subdirName.toUpperCase();
                                        subconsoles.add(new ConsoleInfo(subConsolePath, subDisplayName, subDisplayName, subConsolePath));
                                        Log.i(TAG, "Sous-console trouvée: " + subConsolePath);
                                    }
                                }
                            }
                        }
                    }
                    
                    // Ajouter les sous-consoles à availableConsoles sur le thread UI (sans doublons)
                    runOnUiThread(() -> {
                        for (ConsoleInfo subconsole : subconsoles) {
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
                        // Trier alphabétiquement
                        availableConsoles.sort((a, b) -> a.id.compareTo(b.id));
                        Log.i(TAG, "Total après ajout sous-consoles: " + availableConsoles.size());
                    });
                }
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
                java.io.File gamelibraryDir = new java.io.File("/storage/emulated/0/GameLibrary-Data/");
                Log.i(TAG, "GameLibrary-Data existe: " + gamelibraryDir.exists());
                if (gamelibraryDir.exists() && gamelibraryDir.isDirectory()) {
                    java.io.File[] directories = gamelibraryDir.listFiles(java.io.File::isDirectory);
                    
                    List<ConsoleInfo> scannedConsoles = new ArrayList<>();
                    
                    if (directories != null) {
                        for (java.io.File dir : directories) {
                            String dirName = dir.getName();
                            
                            // Ignorer les répertoires système
                            if (dirName.equals("data") || dirName.equals("emulatorjs") || 
                                dirName.equals("vmnes") || dirName.equals("playlists") ||
                                dirName.equals("saves") || dirName.equals(".cache") ||
                                dirName.equals("cheats") || dirName.equals("media")) {
                                continue;
                            }
                            
                            // Vérifier si gamelist.json existe
                            java.io.File gamelistFile = new java.io.File(dir, "gamelist.json");
                            boolean hasGamelist = gamelistFile.exists();
                            
                            // Scanner les sous-répertoires (ex: fbneo/sega) AVANT d'ajouter le parent
                            java.io.File[] subdirectories = dir.listFiles(java.io.File::isDirectory);
                            boolean hasSubconsoles = false;
                            Log.i(TAG, "=== SCAN SOUS-REPERTOIRES: " + dirName + " ===");
                            Log.i(TAG, "Sous-dossiers trouvés: " + (subdirectories != null ? subdirectories.length : 0));
                            if (subdirectories != null) {
                                for (java.io.File subdir : subdirectories) {
                                    String subdirName = subdir.getName();
                                    Log.i(TAG, "  >>> Vérification: " + dirName + "/" + subdirName);
                                    
                                    // Ignorer les dossiers système et les vestiges EmulationStation
                                    if (subdirName.equals("media") || subdirName.equals("saves") || 
                                        subdirName.equals("data") || subdirName.equals(".cache") ||
                                        subdirName.equals("cfg") || subdirName.equals("hi") || 
                                        subdirName.equals("inp") || subdirName.equals("ctrlr") || 
                                        subdirName.equals("diff") || subdirName.equals("comment") ||
                                        subdirName.equals("mame2003") || subdirName.equals("mame2003-plus") ||
                                        subdirName.equals("mame2010") || subdirName.equals("mame2014") ||
                                        subdirName.equals("fbneo") || subdirName.equals("fba") ||
                                        subdirName.equals("kinst")) {  // Organisateur de fichiers CHD
                                        Log.d(TAG, "    Ignoré (dossier système/EmulationStation)");
                                        continue;
                                    }
                                    
                                    java.io.File subGamelistFile = new java.io.File(subdir, "gamelist.json");
                                    boolean subHasGamelist = subGamelistFile.exists();
                                    boolean subHasRoms = hasRomFiles(subdir);
                                    Log.i(TAG, "    gamelist.json existe: " + subHasGamelist + ", ROMs présentes: " + subHasRoms);
                                    
                                    if (subHasGamelist || subHasRoms) {
                                        String subConsolePath = dirName + "/" + subdirName;
                                        String subDisplayName = getSubconsoleDisplayName(dirName, subdirName);
                                        scannedConsoles.add(new ConsoleInfo(subConsolePath, subDisplayName, subDisplayName, subConsolePath));
                                        Log.i(TAG, "    *** AJOUT SOUS-CONSOLE: " + subConsolePath + " ***");
                                        hasSubconsoles = true;
                                    } else {
                                        Log.i(TAG, "    Pas de gamelist ni ROMs pour " + subdirName);
                                    }
                                }
                            }
                            
                            // Vérifier si le dossier contient des ROMs (même sans gamelist.json)
                            boolean hasRoms = hasRomFiles(dir);
                            
                            // Ajouter la console parent si elle a un gamelist.json OU des ROMs (même si elle a des sous-consoles)
                            if (hasGamelist || hasRoms) {
                                String displayName = getDefaultDisplayName(dirName);
                                scannedConsoles.add(new ConsoleInfo(dirName, dirName.toUpperCase(), displayName, dirName));
                                String mode = hasGamelist ? "" : " [AUTO SCAN]";
                                Log.i(TAG, "Console scannée: " + dirName + (hasSubconsoles ? " (avec sous-consoles)" : "") + mode);
                            } else if (!hasSubconsoles) {
                                // Pas de gamelist, pas de ROMs et pas de sous-consoles, on ignore ce dossier
                                Log.d(TAG, "Dossier ignoré (pas de gamelist ni ROMs ni sous-consoles): " + dirName);
                            }
                        }
                    }
                    
                    // Trier alphabétiquement
                    scannedConsoles.sort((a, b) -> a.id.compareTo(b.id));
                    
                    // Éviter les doublons dans scannedConsoles
                    List<ConsoleInfo> uniqueScannedConsoles = new ArrayList<>();
                    java.util.Set<String> seenScannedIds = new java.util.HashSet<>();
                    java.util.Set<String> seenScannedDirs = new java.util.HashSet<>();
                    
                    for (ConsoleInfo console : scannedConsoles) {
                        if (!seenScannedIds.contains(console.id) && !seenScannedDirs.contains(console.directory)) {
                            uniqueScannedConsoles.add(console);
                            seenScannedIds.add(console.id);
                            seenScannedDirs.add(console.directory);
                        } else {
                            Log.d(TAG, "Console dupliquée ignorée dans scan: " + console.id + " / " + console.directory);
                        }
                    }
                    
                    Log.i(TAG, "Total consoles scannées (après déduplication): " + uniqueScannedConsoles.size());
                    for (ConsoleInfo console : uniqueScannedConsoles) {
                        Log.i(TAG, "  Console: " + console.id + " (" + console.directory + ") | Name: " + console.name + " | FullName: " + console.fullName);
                    }
                    
                    runOnUiThread(() -> {
                        availableConsoles = uniqueScannedConsoles;
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
                    // Si GameLibrary-Data n'existe pas, utiliser les consoles par défaut
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
        switch (consoleId.toLowerCase()) {
            case "nes": return "Nintendo Entertainment System";
            case "snes": return "Super Nintendo Entertainment System";
            case "n64": return "Nintendo 64";
            case "gba": return "Game Boy Advance";
            case "gbc": return "Game Boy Color";
            case "gb": return "Game Boy";
            case "megadrive":
            case "genesis": return "Sega Genesis / Mega Drive";
            case "nds": return "Nintendo DS";
            case "ps1":
            case "psx": return "PlayStation 1";
            case "psp": return "PlayStation Portable";
            case "arcade": return "Arcade (MAME/FBNeo)";
            case "mame": return "MAME Arcade";
            case "fbneo": return "FBNeo Arcade";
            case "atari2600": return "Atari 2600";
            case "atari5200": return "Atari 5200";
            case "atari7800": return "Atari 7800";
            case "atarilynx": return "Atari Lynx";
            case "segacd": return "Sega CD / Mega CD";
            case "gamegear": return "Sega Game Gear";
            case "pce": return "PC Engine / TurboGrafx-16";
            default: return consoleId.toUpperCase();
        }
    }
    
    private String getSubconsoleDisplayName(String parent, String subconsole) {
        // Noms conviviaux pour les sous-consoles (simples, car déjà groupés visuellement)
        String subLower = subconsole.toLowerCase();
        switch (parent.toLowerCase() + "/" + subLower) {
            // FBNeo sub-systems
            case "fbneo/sega": return "Sega";
            case "fbneo/taito": return "Taito";
            case "fbneo/cps1": return "CPS1 (Capcom)";
            case "fbneo/cps2": return "CPS2 (Capcom)";
            case "fbneo/cps3": return "CPS3 (Capcom)";
            case "fbneo/cpiii": return "CPS3 (Capcom)";
            // Default format
            default: return subconsole.toUpperCase();
        }
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
        // Get unique genres
        java.util.Set<String> genres = new java.util.HashSet<>();
        genres.add("Tous");
        for (Game game : games) {
            genres.add(game.getGenre());
        }
        
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
        filteredGames.clear();
        if ("Tous".equals(genre)) {
            filteredGames.addAll(games);
        } else {
            for (Game game : games) {
                if (genre.equals(game.getGenre())) {
                    filteredGames.add(game);
                }
            }
        }
        
        currentFilter = genre;
        filterChip.setText(genre);
        updateGamesCount();
        updateEmptyState();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    private void updateGamesCount() {
        if (gamesCount != null) {
            gamesCount.setText(currentPageGames.size() + " jeux");
        }
        
        // Update pagination
        updatePaginationUI();
    }
    
    private void updateEmptyState() {
        if (emptyState != null) {
            boolean isEmpty = currentPageGames.isEmpty();
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
        int totalPages = (int) Math.ceil((double) filteredGames.size() / gamesPerPage);
        
        if (paginationInfo != null) {
            if (totalPages > 1) {
                paginationInfo.setText("Pages " + (currentPage + 1) + "/" + totalPages);
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
        if (currentPage > 0) {
            currentPage--;
            updateCurrentPage();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateGamesCount();
            updateEmptyState();
            
            // Scroll to top
            recyclerView.smoothScrollToPosition(0);
        }
    }
    
    private void goToNextPage() {
        int totalPages = (int) Math.ceil((double) filteredGames.size() / gamesPerPage);
        if (currentPage < totalPages - 1) {
            currentPage++;
            updateCurrentPage();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateGamesCount();
            updateEmptyState();
            
            // Scroll to top
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
            setupFullscreenMode();
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
            startForegroundService(serviceIntent);
            Log.i(TAG, "WebServerService started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting WebServerService: ", e);
            Toast.makeText(this, "Error starting WebServer: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        
        String[] romExtensions = {
            // Nintendo
            ".nes", ".fds", ".unf",                           // NES/Famicom
            ".smc", ".sfc", ".fig",                           // SNES
            ".n64", ".z64", ".v64",                           // N64
            ".gb", ".gbc",                                    // Game Boy / Color
            ".gba", ".agb",                                   // Game Boy Advance
            ".nds",                                           // Nintendo DS
            
            // Sega
            ".md", ".smd", ".gen", ".bin",                   // Genesis/Mega Drive
            ".sms",                                           // Master System
            ".gg",                                            // Game Gear
            ".32x",                                           // 32X
            ".cue", ".chd",                                   // Sega CD
            
            // Sony
            ".iso", ".img", ".pbp", ".cso", ".PBP",          // PSP/PS1
            
            // Atari
            ".a26", ".a52", ".a78",                          // Atari 2600/5200/7800
            ".lnx",                                           // Atari Lynx
            ".jag",                                           // Atari Jaguar
            
            // Autres
            ".zip", ".7z", ".rar",                           // Archives
            ".rom"                                            // Generic ROM
        };
        
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName().toLowerCase();
                    for (String ext : romExtensions) {
                        if (fileName.endsWith(ext.toLowerCase())) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    // NOTE: Les méthodes de scan ont été déplacées dans SplashActivity
    // pour que le scan se fasse pendant la demande de permissions.
    // Ces méthodes peuvent être supprimées ou gardées pour un scan manuel futur.
}

