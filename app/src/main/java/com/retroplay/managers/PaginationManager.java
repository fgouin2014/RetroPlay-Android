package com.retroplay.managers;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.retroplay.Game;
import com.retroplay.R;
import com.retroplay.ThemeManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager pour la pagination alphabétique des jeux.
 * Extracted from GameListActivity to improve modularity and testability.
 * 
 * Gère:
 * - Création et mise à jour des boutons alphabétiques
 * - Filtrage par lettre
 * - Pagination des résultats
 * - Comptage des jeux par lettre
 */
public class PaginationManager {
    
    private static final String TAG = "PaginationManager";
    
    private final Context context;
    private final ThemeManager themeManager;
    
    private LinearLayout alphabetRow1;
    private LinearLayout alphabetRow2;
    private LinearLayout alphabetRow3;
    
    private String currentLetter = "#";
    private int currentPage = 0;
    private int gamesPerPage = 20;
    
    private List<Game> allGames = new ArrayList<>();
    private List<Game> filteredGames = new ArrayList<>();
    private List<Game> currentPageGames = new ArrayList<>();
    private boolean showOnlyFavorites = false;
    
    /**
     * Interface pour les callbacks
     */
    public interface PaginationCallbacks {
        List<Game> getAllGames();
        List<Game> getFavoriteGames();
        void onFilterChanged(List<Game> filteredGames, List<Game> currentPageGames);
        void onLetterSelected(String letter);
        void onPageChanged(int page);
    }
    
    private final PaginationCallbacks callbacks;
    
    public PaginationManager(Context context, LinearLayout row1, LinearLayout row2, LinearLayout row3,
                             PaginationCallbacks callbacks) {
        this.context = context;
        this.themeManager = ThemeManager.getInstance(context);
        this.alphabetRow1 = row1;
        this.alphabetRow2 = row2;
        this.alphabetRow3 = row3;
        this.callbacks = callbacks;
    }
    
    /**
     * Initialiser la pagination alphabétique
     */
    public void setupAlphabetPagination() {
        String[] row1 = {"#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"};
        String[] row2 = {"L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V"};
        String[] row3 = {"W", "X", "Y", "Z"};
        
        setupAlphabetRow(row1, alphabetRow1);
        setupAlphabetRow(row2, alphabetRow2);
        setupAlphabetRow(row3, alphabetRow3);
        
        // Mettre à jour les états initiaux
        updateAlphabetAvailability();
    }
    
    /**
     * Configurer une rangée de boutons alphabétiques
     */
    private void setupAlphabetRow(String[] letters, LinearLayout row) {
        for (String letter : letters) {
            TextView button = new TextView(context);
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
            
            ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
            int primaryColor = themeManager.getPrimaryColor(context);
            int headerBackgroundColor = themeManager.getHeaderBackgroundColor(context);
            
            button.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            button.setClickable(true);
            button.setFocusable(true);
            
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(6 * context.getResources().getDisplayMetrics().density); // 6dp
            if ("#".equals(letter)) {
                drawable.setColor(primaryColor);
                button.setTextColor(context.getResources().getColor(R.color.kitt_black));
            } else {
                drawable.setColor(headerBackgroundColor);
                button.setTextColor(primaryColor);
            }
            drawable.setStroke((int)(1 * context.getResources().getDisplayMetrics().density), primaryColor);
            button.setBackground(drawable);
            
            button.setOnClickListener(v -> filterByLetter(letter));
            row.addView(button);
        }
    }
    
    /**
     * Filtrer les jeux par lettre
     */
    public void filterByLetter(String letter) {
        currentLetter = letter;
        currentPage = 0;
        
        allGames = callbacks.getAllGames();
        List<Game> baseList = showOnlyFavorites ? callbacks.getFavoriteGames() : allGames;
        
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
        
        Log.i(TAG, "filterByLetter: letter='" + letter + "' filteredGames.size=" + filteredGames.size());
        
        // Pagination
        updateCurrentPage();
        
        // Callbacks
        if (callbacks != null) {
            callbacks.onFilterChanged(filteredGames, currentPageGames);
            callbacks.onLetterSelected(letter);
        }
        
        // Update alphabet button states
        updateAlphabetAvailability();
    }
    
    /**
     * Mettre à jour la page courante
     */
    public void updateCurrentPage() {
        currentPageGames.clear();
        int startIndex = currentPage * gamesPerPage;
        int endIndex = Math.min(startIndex + gamesPerPage, filteredGames.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            currentPageGames.add(filteredGames.get(i));
        }
        
        if (callbacks != null) {
            callbacks.onPageChanged(currentPage);
        }
    }
    
    /**
     * Compter les jeux par lettre
     */
    private Map<String, Integer> countGamesByLetter() {
        Map<String, Integer> letterCounts = new HashMap<>();
        
        // Initialiser toutes les lettres à 0
        letterCounts.put("#", 0);
        for (char c = 'A'; c <= 'Z'; c++) {
            letterCounts.put(String.valueOf(c), 0);
        }
        
        allGames = callbacks.getAllGames();
        List<Game> baseList = showOnlyFavorites ? callbacks.getFavoriteGames() : allGames;
        
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
    public void updateAlphabetAvailability() {
        Map<String, Integer> letterCounts = countGamesByLetter();
        
        updateAlphabetRowAvailability(alphabetRow1, letterCounts);
        updateAlphabetRowAvailability(alphabetRow2, letterCounts);
        updateAlphabetRowAvailability(alphabetRow3, letterCounts);
    }
    
    /**
     * Mettre à jour la disponibilité des boutons dans une rangée
     */
    private void updateAlphabetRowAvailability(LinearLayout row, Map<String, Integer> letterCounts) {
        ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
        int primaryColor = themeManager.getPrimaryColor(context);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(context);
        int blackColor = context.getResources().getColor(R.color.kitt_black);
        float density = context.getResources().getDisplayMetrics().density;
        
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView button = (TextView) row.getChildAt(i);
            String letter = button.getText().toString();
            int count = letterCounts.getOrDefault(letter, 0);
            boolean hasGames = count > 0;
            
            // Create drawable programmatically with theme colors
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(6 * density);
            drawable.setStroke((int)(1 * density), primaryColor);
            
            if (hasGames) {
                button.setClickable(true);
                button.setAlpha(1.0f);
                
                if (letter.equals(currentLetter)) {
                    drawable.setColor(primaryColor);
                    button.setBackground(drawable);
                    button.setTextColor(blackColor);
                } else {
                    drawable.setColor(headerBackgroundColor);
                    button.setBackground(drawable);
                    button.setTextColor(primaryColor);
                }
            } else {
                button.setClickable(false);
                button.setAlpha(0.25f);
                drawable.setColor(headerBackgroundColor);
                button.setBackground(drawable);
                button.setTextColor(primaryColor);
            }
        }
    }
    
    /**
     * Sélectionner automatiquement la première lettre disponible
     */
    public void autoSelectFirstAvailableLetter() {
        allGames = callbacks.getAllGames();
        
        // Essayer d'abord les jeux commençant par des chiffres
        boolean hasNumberGames = false;
        for (Game game : allGames) {
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
        
        // Si aucun jeu avec des chiffres, parcourir A-Z
        String[] letters = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", 
                           "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        
        for (String letter : letters) {
            boolean hasGames = false;
            for (Game game : allGames) {
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
        updateAlphabetAvailability();
    }
    
    /**
     * Aller à la page précédente
     */
    public void goToPreviousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateCurrentPage();
        }
    }
    
    /**
     * Aller à la page suivante
     */
    public void goToNextPage() {
        int totalPages = (int) Math.ceil((double) filteredGames.size() / gamesPerPage);
        if (currentPage < totalPages - 1) {
            currentPage++;
            updateCurrentPage();
        }
    }
    
    /**
     * Obtenir les informations de pagination
     */
    public PaginationInfo getPaginationInfo() {
        int totalPages = (int) Math.ceil((double) filteredGames.size() / gamesPerPage);
        return new PaginationInfo(
            currentPage + 1,
            totalPages,
            filteredGames.size(),
            currentPage * gamesPerPage + 1,
            Math.min((currentPage + 1) * gamesPerPage, filteredGames.size())
        );
    }
    
    /**
     * Classe pour les informations de pagination
     */
    public static class PaginationInfo {
        public final int currentPage;
        public final int totalPages;
        public final int totalGames;
        public final int startIndex;
        public final int endIndex;
        
        public PaginationInfo(int currentPage, int totalPages, int totalGames, int startIndex, int endIndex) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.totalGames = totalGames;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
    
    // Getters et setters
    public String getCurrentLetter() { return currentLetter; }
    public int getCurrentPage() { return currentPage; }
    public int getGamesPerPage() { return gamesPerPage; }
    public void setGamesPerPage(int gamesPerPage) { this.gamesPerPage = gamesPerPage; }
    public List<Game> getFilteredGames() { return filteredGames; }
    public List<Game> getCurrentPageGames() { return currentPageGames; }
    public void setShowOnlyFavorites(boolean showOnlyFavorites) { 
        this.showOnlyFavorites = showOnlyFavorites;
        // Re-filter avec la nouvelle préférence
        filterByLetter(currentLetter);
    }
}
















