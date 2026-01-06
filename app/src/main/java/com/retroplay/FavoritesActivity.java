package com.retroplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity pour afficher tous les jeux favoris, toutes consoles confondues
 */
public class FavoritesActivity extends AppCompatActivity implements GameAdapter.OnGameClickListener {
    private static final String TAG = "FavoritesActivity";
    
    private RecyclerView recyclerView;
    private TextView backButton;
    private TextView clearAllButton;
    private TextView sortButton;
    private TextView favoritesCount;
    private View emptyState;
    private ProgressBar loadingProgress;
    
    private List<Game> favoriteGames = new ArrayList<>();
    private GameAdapter adapter;
    private FavoritesManager favoritesManager;
    
    private String currentSort = "A-Z"; // A-Z, Z-A, CONSOLE

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Mode plein écran
        setupFullscreenMode();
        
        setContentView(R.layout.activity_favorites);
        
        // Initialiser le manager des favoris
        favoritesManager = FavoritesManager.getInstance(this);
        
        setupViews();
        setupRecyclerView();
        loadFavorites();
    }
    
    private void setupFullscreenMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
    
    private void setupViews() {
        backButton = findViewById(R.id.backButton);
        clearAllButton = findViewById(R.id.clearAllButton);
        sortButton = findViewById(R.id.sortButton);
        favoritesCount = findViewById(R.id.favoritesCount);
        emptyState = findViewById(R.id.emptyState);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        backButton.setOnClickListener(v -> finish());
        clearAllButton.setOnClickListener(v -> showClearAllDialog());
        sortButton.setOnClickListener(v -> toggleSort());
    }
    
    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(2, 8, true));
    }
    
    private void loadFavorites() {
        loadingProgress.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        
        new Thread(() -> {
            // Utiliser LoadFavoritesUseCase pour charger les favoris depuis toutes les consoles
            android.content.SharedPreferences prefs = getSharedPreferences("game_library_prefs", MODE_PRIVATE);
            com.retroplay.usecases.LoadFavoritesUseCase loadFavoritesUseCase = 
                new com.retroplay.usecases.LoadFavoritesUseCase(this, prefs);
            com.retroplay.usecases.LoadFavoritesUseCase.LoadFavoritesResult result = 
                loadFavoritesUseCase.loadAllFavorites();
            
            List<Game> allFavorites = result.favorites;
            
            if (!result.success) {
                Log.e(TAG, "Failed to load favorites: " + result.errorMessage);
                allFavorites = new ArrayList<>();
            }
            
            // Créer une variable finale pour la lambda
            final List<Game> finalFavorites = allFavorites;
            
            Log.i(TAG, "Total favorites loaded: " + finalFavorites.size());
            
            runOnUiThread(() -> {
                favoriteGames = finalFavorites;
                sortGames();
                
                adapter = new GameAdapter(favoriteGames, this);
                adapter.setFavoritesManager(favoritesManager);
                recyclerView.setAdapter(adapter);
                
                updateUI();
                loadingProgress.setVisibility(View.GONE);
                
                if (favoriteGames.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }
    
    private void toggleSort() {
        if ("A-Z".equals(currentSort)) {
            currentSort = "Z-A";
            sortButton.setText("SORT: Z-A");
        } else if ("Z-A".equals(currentSort)) {
            currentSort = "CONSOLE";
            sortButton.setText("SORT: CONSOLE");
        } else {
            currentSort = "A-Z";
            sortButton.setText("SORT: A-Z");
        }
        
        sortGames();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        Log.i(TAG, "Sort changed to: " + currentSort);
    }
    
    private void sortGames() {
        if (favoriteGames.isEmpty()) {
            return;
        }
        
        switch (currentSort) {
            case "A-Z":
                Collections.sort(favoriteGames, (g1, g2) -> 
                    g1.getName().compareToIgnoreCase(g2.getName()));
                break;
                
            case "Z-A":
                Collections.sort(favoriteGames, (g1, g2) -> 
                    g2.getName().compareToIgnoreCase(g1.getName()));
                break;
                
            case "CONSOLE":
                Collections.sort(favoriteGames, (g1, g2) -> {
                    int consoleCompare = g1.getConsole().compareToIgnoreCase(g2.getConsole());
                    if (consoleCompare != 0) {
                        return consoleCompare;
                    }
                    return g1.getName().compareToIgnoreCase(g2.getName());
                });
                break;
        }
    }
    
    private void showClearAllDialog() {
        if (favoriteGames.isEmpty()) {
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Clear All Favorites?")
            .setMessage("Are you sure you want to remove all " + favoriteGames.size() + " favorites?")
            .setPositiveButton("Clear All", (dialog, which) -> {
                favoritesManager.clearAllFavorites();
                favoriteGames.clear();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                updateUI();
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                Log.i(TAG, "All favorites cleared");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void updateUI() {
        int count = favoriteGames.size();
        favoritesCount.setText(count + (count == 1 ? " favorite" : " favorites"));
    }
    
    @Override
    public void onClick(Game game) {
        Log.i(TAG, "Game selected: " + game.getTitle());
        
        Intent intent = new Intent(this, GameDetailsActivity.class);
        intent.putExtra("game", game);
        startActivity(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les favoris au cas où ils aient été modifiés
        if (adapter != null) {
            loadFavorites();
        }
    }
}

