package com.retroplay.helpers;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.retroplay.R;
import com.retroplay.ThemeManager;

/**
 * Helper pour appliquer le thème aux vues de GameListActivity.
 * Extracted from GameListActivity to improve modularity and reusability.
 * 
 * Gère:
 * - Application des couleurs du thème aux composants UI
 * - Création de drawables avec les couleurs du thème
 * - Mise à jour de tous les éléments visuels
 */
public class ThemeApplicator {
    
    private final Context context;
    private final ThemeManager themeManager;
    
    public ThemeApplicator(Context context) {
        this.context = context;
        this.themeManager = ThemeManager.getInstance(context);
    }
    
    /**
     * Appliquer le thème à tous les composants de GameListActivity
     */
    public void applyTheme(ThemeViews views) {
        int primaryColor = themeManager.getPrimaryColor(context);
        int mediumColor = themeManager.getMediumColor(context);
        int lightColor = themeManager.getLightColor(context);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(context);
        float density = context.getResources().getDisplayMetrics().density;
        
        // Appliquer au header (background)
        if (views.headerView != null && views.headerView.getParent() instanceof ViewGroup) {
            ViewGroup headerParent = (ViewGroup) views.headerView.getParent();
            headerParent.setBackgroundColor(headerBackgroundColor);
        }
        
        // Console Selector Button
        if (views.consoleSelectorButton != null) {
            applyThemeToTextView(views.consoleSelectorButton, primaryColor, headerBackgroundColor, density);
        }
        
        // Console Config Button
        if (views.consoleConfigButton != null) {
            applyThemeToMaterialButton(views.consoleConfigButton, primaryColor, headerBackgroundColor);
        }
        
        // Console Manager Button
        if (views.consoleManagerButton != null) {
            applyThemeToMaterialButton(views.consoleManagerButton, primaryColor, headerBackgroundColor);
        }
        
        // Favorites Button
        if (views.favoritesButton != null) {
            applyThemeToMaterialButton(views.favoritesButton, primaryColor, headerBackgroundColor);
        }
        
        // Search Toggle Button
        if (views.searchToggleButton != null) {
            applyThemeToMaterialButton(views.searchToggleButton, primaryColor, headerBackgroundColor);
        }
        
        // Search Input
        if (views.searchInput != null) {
            views.searchInput.setTextColor(primaryColor);
            views.searchInput.setHintTextColor(lightColor);
            views.searchInput.setAlpha(1.0f);
        }
        
        // Games Count
        if (views.gamesCount != null) {
            views.gamesCount.setTextColor(themeManager.getTextSecondaryColor(context));
            views.gamesCount.setAlpha(1.0f);
        }
        
        // Filter Chip
        if (views.filterChip != null) {
            applyThemeToTextView(views.filterChip, primaryColor, headerBackgroundColor, density);
        }
        
        // Favorites Filter Button
        if (views.favoritesFilterButton != null) {
            views.favoritesFilterButton.setAlpha(1.0f);
            if (views.showOnlyFavorites) {
                views.favoritesFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                views.favoritesFilterButton.setIconTintResource(R.color.kitt_black);
            } else {
                views.favoritesFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
                int primaryColorResId = themeManager.getPrimaryColorResId(context);
                if (primaryColorResId != 0) {
                    views.favoritesFilterButton.setIconTintResource(primaryColorResId);
                } else {
                    views.favoritesFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
                }
            }
        }
        
        // Loading Progress
        if (views.loadingProgress != null) {
            views.loadingProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(primaryColor));
        }
        
        // Empty State
        if (views.emptyStateTitle != null) {
            views.emptyStateTitle.setTextColor(themeManager.getTextPrimaryColor(context));
            views.emptyStateTitle.setAlpha(1.0f);
        }
        if (views.emptyStateSubtitle != null) {
            views.emptyStateSubtitle.setTextColor(themeManager.getTextSecondaryColor(context));
            views.emptyStateSubtitle.setAlpha(1.0f);
        }
        
        // FAB Random
        if (views.fabRandom != null) {
            views.fabRandom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            views.fabRandom.setImageTintList(android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.kitt_black)));
            views.fabRandom.setAlpha(1.0f);
        }
        
        // Pagination Footer
        if (views.paginationFooter != null) {
            views.paginationFooter.setBackgroundColor(headerBackgroundColor);
        }
        
        // Pagination Buttons
        if (views.paginationPrev != null) {
            applyThemeToTextView(views.paginationPrev, primaryColor, headerBackgroundColor, density);
        }
        if (views.paginationInfo != null) {
            views.paginationInfo.setTextColor(primaryColor);
            views.paginationInfo.setAlpha(1.0f);
        }
        if (views.paginationNext != null) {
            applyThemeToTextView(views.paginationNext, primaryColor, headerBackgroundColor, density);
        }
    }
    
    /**
     * Appliquer le thème à un TextView avec drawable
     */
    private void applyThemeToTextView(TextView textView, int primaryColor, int headerBackgroundColor, float density) {
        textView.setTextColor(primaryColor);
        textView.setAlpha(1.0f);
        
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(6 * density); // 6dp
        drawable.setColor(headerBackgroundColor);
        drawable.setStroke((int)(1 * density), primaryColor); // 1dp stroke
        textView.setBackground(drawable);
    }
    
    /**
     * Appliquer le thème à un MaterialButton
     */
    private void applyThemeToMaterialButton(MaterialButton button, int primaryColor, int headerBackgroundColor) {
        button.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
        button.setAlpha(1.0f);
    }
    
    /**
     * Classe pour regrouper toutes les vues à thématiser
     */
    public static class ThemeViews {
        public View headerView;
        public TextView consoleSelectorButton;
        public MaterialButton consoleConfigButton;
        public MaterialButton consoleManagerButton;
        public MaterialButton favoritesButton;
        public MaterialButton searchToggleButton;
        public EditText searchInput;
        public TextView gamesCount;
        public TextView filterChip;
        public MaterialButton favoritesFilterButton;
        public android.widget.ProgressBar loadingProgress;
        public TextView emptyStateTitle;
        public TextView emptyStateSubtitle;
        public FloatingActionButton fabRandom;
        public View paginationFooter;
        public TextView paginationPrev;
        public TextView paginationInfo;
        public TextView paginationNext;
        public boolean showOnlyFavorites;
        
        public ThemeViews() {
        }
    }
}
















