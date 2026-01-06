package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;
import com.retroplay.Game;
import com.retroplay.helpers.MetadataResolver;
import com.retroplay.helpers.RomPathResolver;
import com.retroplay.util.PsxSerialExtractor;

/**
 * Use Case pour charger et afficher les métadonnées d'un jeu.
 * Extracted from GameDetailsActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement des métadonnées depuis gamelist.json
 * - Extraction et affichage du serial PSX
 * - Mise à jour des TextViews avec les métadonnées
 */
public class LoadGameMetadataUseCase {
    
    private static final String TAG = "LoadGameMetadataUseCase";
    
    private final Context context;
    
    /**
     * Interface pour les callbacks UI
     */
    public interface MetadataCallbacks {
        void runOnUiThread(Runnable action);
        void setCurrentPsxSerial(String serial);
        String getCurrentPsxSerial();
    }
    
    private final MetadataCallbacks callbacks;
    
    public LoadGameMetadataUseCase(Context context, MetadataCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }
    
    /**
     * Classe pour regrouper les vues à mettre à jour
     */
    public static class MetadataViews {
        public TextView gameDescription;
        public TextView gameGenre;
        public TextView gamePlayers;
        public TextView gameReleaseDate;
        public TextView gameRating;
        
        public MetadataViews() {
        }
    }
    
    /**
     * Met à jour les vues avec les métadonnées du jeu
     */
    public void updateMetadataViews(Game game, MetadataViews views) {
        if (game == null) {
            Log.w(TAG, "Game is null, cannot update metadata");
            return;
        }
        
        // Description (utilise MetadataResolver)
        if (views.gameDescription != null) {
            views.gameDescription.setText(MetadataResolver.resolveDescription(game));
        }
        
        // Genre (utilise MetadataResolver)
        if (views.gameGenre != null) {
            views.gameGenre.setText(MetadataResolver.resolveGenre(game));
        }
        
        // Nombre de joueurs (utilise MetadataResolver)
        if (views.gamePlayers != null) {
            views.gamePlayers.setText(MetadataResolver.resolvePlayers(game));
        }
        
        // Date de sortie avec développeur et éditeur (utilise MetadataResolver)
        if (views.gameReleaseDate != null) {
            views.gameReleaseDate.setText(MetadataResolver.resolveReleaseDate(game));
        }
        
        // Rating si disponible (utilise MetadataResolver)
        if (views.gameRating != null) {
            String rating = MetadataResolver.resolveRating(game);
            if (rating != null) {
                views.gameRating.setText(rating);
                views.gameRating.setVisibility(android.view.View.VISIBLE);
            } else {
                views.gameRating.setVisibility(android.view.View.GONE);
            }
        }
        
        // Try to fetch and display PSX Serial (asynchronously)
        fetchAndDisplayPsxSerial(game, views.gameDescription);
    }
    
    /**
     * Helper to asynchronously fetch and display PSX Serial if applicable
     */
    private void fetchAndDisplayPsxSerial(Game game, TextView descriptionView) {
        if (game == null) {
            return;
        }
        
        String console = game.getConsole().toLowerCase();
        // Check for PSX console variants
        if (!console.equals("psx") && !console.equals("ps1") && !console.equals("playstation")) {
            return;
        }
        
        new Thread(() -> {
            String romPath = RomPathResolver.resolveRomPath(game);
            if (romPath != null) {
                // Use Kotlin object instance from Java
                String serial = PsxSerialExtractor.INSTANCE.extractSerial(romPath);
                if (serial != null) {
                    callbacks.runOnUiThread(() -> {
                        callbacks.setCurrentPsxSerial(serial); // Store for Per-Game Config
                        
                        if (descriptionView != null) {
                            String currentDesc = descriptionView.getText().toString();
                            if (!currentDesc.contains("Serial: " + serial)) {
                                // Append Serial to description
                                descriptionView.setText(currentDesc + "\n💿 Serial: " + serial);
                                Log.i(TAG, "[PSX] Displaying Serial: " + serial);
                            }
                        }
                    });
                }
            }
        }).start();
    }
    
    /**
     * Obtient le hash/CRC du jeu pour stockage
     */
    public String getGameHash(Game game) {
        return game != null ? game.getHash() : null;
    }
}
















