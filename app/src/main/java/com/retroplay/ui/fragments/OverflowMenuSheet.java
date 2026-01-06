package com.retroplay.ui.fragments;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.retroplay.R;
import com.retroplay.ThemeManager;
import com.retroplay.WebServerPreferences;

/**
 * Overflow menu sheet pour GameListActivity
 * Extrait de GameListActivity pour modularité
 */
public class OverflowMenuSheet {
    
    public interface OverflowMenuCallbacks {
        void onOpenConsoleGallery();
        void onOpenFavorites();
        void onOpenConsoleManager();
        void onOpenConsoleConfig();
        void onSelectRandomGame();
        void onShowPagination();
        void onToggleSearchScope();
        void onShowThemeSelector();
        void onStartWebServerService();
        String onGetLocalIpAddress();
    }
    
    /**
     * Affiche le menu overflow
     */
    public static void show(
            AppCompatActivity activity,
            boolean searchAllConsoles,
            OverflowMenuCallbacks callbacks) {
        
        if (activity == null || callbacks == null) {
            return;
        }
        
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        
        // Appliquer le thème au BottomSheetDialog
        ThemeManager themeManager = ThemeManager.getInstance(activity);
        int primaryColor = themeManager.getPrimaryColor(activity);
        int backgroundColor = activity.getResources().getColor(R.color.kitt_black);
        
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(backgroundColor);
        
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(backgroundColor);
        int pad = (int) (activity.getResources().getDisplayMetrics().density * 16);
        container.setPadding(pad, pad, pad, pad);
        
        // Helper to add a button
        java.util.function.BiConsumer<String, Runnable> addItem = (label, action) -> {
            MaterialButton btn = new MaterialButton(
                activity, 
                null, 
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            btn.setText(label);
            btn.setTextColor(primaryColor);
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
            btn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            );
            btn.setRippleColor(android.content.res.ColorStateList.valueOf(primaryColor));
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.topMargin = pad / 2;
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                sheet.dismiss();
                action.run();
            });
            container.addView(btn);
        };
        
        addItem.accept("Open Gallery (Console)", callbacks::onOpenConsoleGallery);
        addItem.accept("Favorites", callbacks::onOpenFavorites);
        addItem.accept("Console Manager", callbacks::onOpenConsoleManager);
        addItem.accept("Console Config", callbacks::onOpenConsoleConfig);
        addItem.accept("Random Game", callbacks::onSelectRandomGame);
        addItem.accept("Pagination…", callbacks::onShowPagination);
        addItem.accept(
            searchAllConsoles ? "Search Scope: ALL (tap to switch)" : "Search Scope: CURRENT (tap to switch)",
            callbacks::onToggleSearchScope
        );
        addItem.accept(
            "Theme: " + themeManager.getCurrentTheme().getDisplayName() + " (tap to change)",
            callbacks::onShowThemeSelector
        );
        
        // Option pour servir la bibliothèque sur le réseau
        WebServerPreferences webServerPrefs = WebServerPreferences.getInstance(activity);
        boolean networkMode = webServerPrefs.isNetworkServerEnabled();
        addItem.accept(
            "Network Server: " + (networkMode ? "ON (tap to disable)" : "OFF (tap to enable)"),
            () -> {
                webServerPrefs.setNetworkServerEnabled(!networkMode);
                if (!networkMode) {
                    // Démarrer le serveur si activé
                    callbacks.onStartWebServerService();
                    Toast.makeText(
                        activity,
                        "Network Server: ON\nAccess: http://" + callbacks.onGetLocalIpAddress() + ":7777/",
                        Toast.LENGTH_LONG
                    ).show();
                } else {
                    // Arrêter le serveur si désactivé
                    Toast.makeText(
                        activity,
                        "Network Server: OFF\nServer will stop when WASM games close",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
        
        scrollView.addView(container, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        sheet.setContentView(scrollView);
        
        // Appliquer le thème au fond du BottomSheetDialog
        android.view.View bottomSheet = sheet.findViewById(
            com.google.android.material.R.id.design_bottom_sheet
        );
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(backgroundColor);
        }
        
        sheet.show();
    }
}

