
package com.retroplay;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
    private TextView backButton;
    private TextView consoleSelectorButton;
    private TextView consoleConfigButton;
    private TextView consoleManagerButton;
    private RecyclerView recyclerView;
    private EditText searchInput;
    private TextView searchToggleButton;
    private TextView searchScopeToggle;
    private TextView paginationPrev;
    private TextView paginationInfo;
    private TextView paginationNext;
    private FloatingActionButton fabRandom;
    private ProgressBar loadingProgress;
    private TextView gamesCount;
    private TextView filterChip;
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
    
    // Console Selection
    private String currentConsole = "nes"; // Default: NES (will be overridden by saved preference)
    private List<ConsoleInfo> availableConsoles = new ArrayList<>();
    
    // SharedPreferences pour la persistance
    private android.content.SharedPreferences consolePrefs;
    
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Mode plein écran - masquer barre d'état et navigation
        setupFullscreenMode();
        
        setContentView(R.layout.activity_game_list);
        
        setupToolbar();
        setupViews();
        setupRecyclerView();
        
        // Démarrer le WebServerService (port 7777) pour EmulatorJS
        startWebServerService();
        
        // Initialiser SharedPreferences pour la persistance du choix de console
        consolePrefs = getSharedPreferences("game_library_prefs", MODE_PRIVATE);
        
        // Charger la dernière console sélectionnée (ou "nes" par défaut)
        currentConsole = consolePrefs.getString("last_selected_console", "nes");
        Log.i(TAG, "Restored last selected console: " + currentConsole);
        
        // Copier les HTML par défaut vers le stockage si nécessaire
        copyDefaultHtmlToStorage();
        
        // Charger les consoles depuis l'API en arrière-plan
        loadAvailableConsoles();
        updateConsoleTitle();
        
        // Charger les jeux
        loadGames();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les consoles au cas où elles aient été modifiées dans Console Manager
        loadAvailableConsoles();
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
               backButton = findViewById(R.id.backButton);
               consoleSelectorButton = findViewById(R.id.consoleSelectorButton);
               consoleConfigButton = findViewById(R.id.consoleConfigButton);
               consoleManagerButton = findViewById(R.id.consoleManagerButton);
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
               emptyState = findViewById(R.id.emptyState);
               emptyStateTitle = findViewById(R.id.emptyStateTitle);
               emptyStateSubtitle = findViewById(R.id.emptyStateSubtitle);
        
        // Setup search functionality
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterGames(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
               // Setup back button
               backButton.setOnClickListener(v -> finish());
               
               // Setup console selector
               consoleSelectorButton.setOnClickListener(v -> showConsoleSelector());
               
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
               
               // Setup alphabetical pagination
               setupAlphabetPagination();
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
                
                // Lire le gamelist.json depuis le stockage interne selon la console sélectionnée
                // Support des sous-consoles (ex: fbneo/sega)
                String gamelistPath = "/storage/emulated/0/GameLibrary-Data/" + currentConsole + "/gamelist.json";
                java.io.File gamelistFile = new java.io.File(gamelistPath);
                
                if (!gamelistFile.exists()) {
                    Log.i(TAG, "Fichier gamelist.json non trouvé: " + gamelistPath);
                    Log.i(TAG, "Tentative AUTO SCAN via API serveur...");
                    
                    // Try to load auto-generated gamelist from server
                    try {
                        java.net.URL autoScanUrl = new java.net.URL("http://localhost:7777/gamedata/" + currentConsole + "/gamelist.json");
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
                            
                            // Parse the auto-generated JSON
                            parseAndDisplayGames(autoResponse.toString());
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

                // Parse and display games
                parseAndDisplayGames(json);

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
    
    private void parseAndDisplayGames(String jsonString) {
        try {
            // Parse JSON object format: {"games": [...]}
            jsonString = jsonString.trim();
            JSONObject jsonObj = new JSONObject(jsonString);
            JSONArray arr = jsonObj.getJSONArray("games");
            
            List<Game> tempGames = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Game game = new Game(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("path"),
                        obj.getString("desc"),
                        obj.getString("releasedate"),
                        obj.getString("genre"),
                        obj.getString("players")
                );
                // Définir la console pour ce jeu
                game.setConsole(currentConsole);
                // Initialiser les chemins vers les images
                game.initializePaths(null);
                tempGames.add(game);
            }

            // Sort games alphabetically
            tempGames.sort((g1, g2) -> g1.getName().compareToIgnoreCase(g2.getName()));
            
            runOnUiThread(() -> {
                games = tempGames;
                // Auto-selectionner la premiere lettre disponible
                autoSelectFirstAvailableLetter();
                
                adapter = new GameAdapter(currentPageGames, this);
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
        startActivity(intent);
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
                                    Game game = new Game(
                                        obj.getString("id"),
                                        gameName,
                                        obj.getString("path"),
                                        obj.getString("desc"),
                                        obj.getString("releasedate"),
                                        obj.getString("genre"),
                                        obj.getString("players")
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
            searchToggleButton.setText("✖");
            
            // Afficher le mode de recherche actuel
            updateSearchPlaceholder();
        } else {
            searchInputLayout.setVisibility(View.GONE);
            searchToggleButton.setText("🔍");
            searchInput.setText(""); // Clear search
            filterGames(""); // Reset filter
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
            if (searchAllConsoles) {
                searchScopeToggle.setText("ALL");
                searchScopeToggle.setTextColor(getResources().getColor(R.color.kitt_green));
            } else {
                String shortName = currentConsole.length() > 4 ? currentConsole.substring(0, 4).toUpperCase() : currentConsole.toUpperCase();
                searchScopeToggle.setText(shortName);
                searchScopeToggle.setTextColor(getResources().getColor(R.color.kitt_red_light));
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
            
            if ("#".equals(letter)) {
                button.setBackgroundColor(getResources().getColor(R.color.kitt_red));
                button.setTextColor(getResources().getColor(R.color.kitt_black));
            } else {
                button.setBackgroundColor(getResources().getColor(R.color.kitt_medium_red));
                button.setTextColor(getResources().getColor(R.color.kitt_red));
            }
            
            button.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            button.setClickable(true);
            button.setFocusable(true);
            button.setBackgroundResource(R.drawable.filter_chip_background);
            
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
        
        // Compter les jeux
        for (Game game : games) {
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
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView button = (TextView) row.getChildAt(i);
            String letter = button.getText().toString();
            int count = letterCounts.getOrDefault(letter, 0);
            boolean hasGames = count > 0;
            
            if (hasGames) {
                // Lettre avec jeux - active et opaque
                button.setClickable(true);
                button.setAlpha(1.0f);
                
                // Si c'est la lettre sélectionnée, la mettre en surbrillance
                if (letter.equals(currentLetter)) {
                    button.setBackgroundColor(getResources().getColor(R.color.kitt_red));
                    button.setTextColor(getResources().getColor(R.color.kitt_black));
                } else {
                    button.setBackgroundColor(getResources().getColor(R.color.kitt_medium_red));
                    button.setTextColor(getResources().getColor(R.color.kitt_red));
                }
            } else {
                // Lettre sans jeux - désactivée et transparente (garde les mêmes couleurs)
                button.setClickable(false);
                button.setAlpha(0.25f);
                
                // Garder les couleurs normales (non sélectionnées)
                button.setBackgroundColor(getResources().getColor(R.color.kitt_medium_red));
                button.setTextColor(getResources().getColor(R.color.kitt_red));
            }
        }
    }
    
    private void filterByLetter(String letter) {
        currentLetter = letter;
        currentPage = 0;
        
        filteredGames.clear();
        if ("#".equals(letter)) {
            // Show games starting with numbers
            for (Game game : games) {
                String firstChar = game.getName().substring(0, 1).toUpperCase();
                if (Character.isDigit(firstChar.charAt(0))) {
                    filteredGames.add(game);
                }
            }
        } else {
            // Show games starting with the selected letter
            for (Game game : games) {
                String firstChar = game.getName().substring(0, 1).toUpperCase();
                if (firstChar.equals(letter)) {
                    filteredGames.add(game);
                }
            }
        }
        
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
        
        for (int i = startIndex; i < endIndex; i++) {
            currentPageGames.add(filteredGames.get(i));
        }
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
        Snackbar.make(recyclerView, "Jeu aléatoire: " + randomGame.getName(), Snackbar.LENGTH_LONG)
                .setAction("JOUER", v -> onClick(randomGame))
                .setActionTextColor(getResources().getColor(R.color.kitt_red))
                .show();
    }
    
    private void openConsoleConfig() {
        Intent intent = new Intent(this, ConsoleConfigActivity.class);
        intent.putExtra("console", currentConsole);
        startActivity(intent);
    }
    
    private void openConsoleManager() {
        Intent intent = new Intent(this, ConsoleManagerActivity.class);
        startActivityForResult(intent, 100); // Request code 100 pour Console Manager
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Retour du Console Manager - rafraîchir la liste
        if (requestCode == 100) {
            Log.i(TAG, "Retour du Console Manager - Rafraîchissement de la liste");
            
            // Recharger les consoles disponibles
            loadAvailableConsoles();
            
            // Recharger les jeux de la console actuelle
            games.clear();
            filteredGames.clear();
            currentPageGames.clear();
            loadGames();
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
                // Afficher seulement le fullName pour éviter la redondance
                itemsList.add(console.fullName);
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
        
        // Load games for new console
        loadGames();
    }
    
    private void loadAvailableConsoles() {
        new Thread(() -> {
            try {
                // Appeler l'API pour obtenir les consoles disponibles
                java.net.URL url = new java.net.URL("http://localhost:7777/gamelibrary/api/consoles");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject jsonObj = new JSONObject(response.toString());
                    JSONArray consolesArray = jsonObj.getJSONArray("consoles");
                    
                    List<ConsoleInfo> tempConsoles = new ArrayList<>();
                    for (int i = 0; i < consolesArray.length(); i++) {
                        JSONObject consoleObj = consolesArray.getJSONObject(i);
                        if (consoleObj.getBoolean("enabled")) {
                            tempConsoles.add(new ConsoleInfo(
                                consoleObj.getString("id"),
                                consoleObj.getString("name"),
                                consoleObj.getString("fullName"),
                                consoleObj.getString("directory")
                            ));
                        }
                    }
                    
                    runOnUiThread(() -> {
                        availableConsoles = tempConsoles;
                        Log.i(TAG, "Consoles chargées depuis serveur: " + availableConsoles.size());
                        
                        // Scanner et ajouter les sous-consoles localement
                        scanAndAddSubconsoles();
                        
                        Log.i(TAG, "Total consoles (avec sous-consoles): " + availableConsoles.size());
                        for (ConsoleInfo console : availableConsoles) {
                            Log.i(TAG, "  - " + console.id + ": " + console.fullName);
                        }
                    });
                } else {
                    Log.e(TAG, "Erreur API consoles: " + conn.getResponseCode());
                    // Fallback: consoles par défaut
                    runOnUiThread(() -> setDefaultConsoles());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Erreur chargement consoles", e);
                // Fallback: consoles par défaut
                runOnUiThread(() -> setDefaultConsoles());
            }
        }).start();
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
                    
                    Log.i(TAG, "Total consoles scannées: " + scannedConsoles.size());
                    for (ConsoleInfo console : scannedConsoles) {
                        Log.i(TAG, "  Console: " + console.id + " | Name: " + console.name + " | FullName: " + console.fullName);
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
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur scan consoles", e);
                runOnUiThread(() -> {
                    availableConsoles.add(new ConsoleInfo("nes", "NES", "Nintendo Entertainment System", "nes"));
                    availableConsoles.add(new ConsoleInfo("snes", "SNES", "Super Nintendo Entertainment System", "snes"));
                    availableConsoles.add(new ConsoleInfo("n64", "N64", "Nintendo 64", "n64"));
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
     * Copie les fichiers HTML par défaut depuis les assets vers le stockage interne
     * si ils n'existent pas déjà (permet la personnalisation)
     */
    private void copyDefaultHtmlToStorage() {
        String basePath = "/storage/emulated/0/ChatAI-Files/sites/";
        String[] htmlFiles = {"index.html", "emulator.html"};
        
        for (String fileName : htmlFiles) {
            try {
                File targetFile = new File(basePath + fileName);
                
                // Ne copier que si le fichier n'existe pas déjà
                if (!targetFile.exists()) {
                    Log.d(TAG, "Copying default " + fileName + " to storage");
                    
                    // Lire depuis les assets
                    InputStream is = getAssets().open("gamelibrary/" + fileName);
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    
                    // Écrire vers le stockage
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
                    fos.write(buffer);
                    fos.close();
                    
                    Log.d(TAG, "Copied " + fileName + " to storage successfully");
                } else {
                    Log.d(TAG, fileName + " already exists in storage, using custom version");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error copying " + fileName + ": " + e.getMessage());
            }
        }
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
}

