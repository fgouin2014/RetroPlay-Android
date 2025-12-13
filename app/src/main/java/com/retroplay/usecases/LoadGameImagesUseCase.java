package com.retroplay.usecases;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.retroplay.Game;

/**
 * Use Case pour charger les images d'un jeu
 * Extrait de GameDetailsActivity pour améliorer la modularité
 */
public class LoadGameImagesUseCase {
    
    /**
     * Charge les images du jeu dans les ImageView fournies
     * @param game L'objet Game contenant les informations du jeu
     * @param screenshotView ImageView pour la capture d'écran (background)
     * @param box2dView ImageView pour l'image box2d
     */
    public void loadGameImages(Game game, ImageView screenshotView, ImageView box2dView) {
        if (game == null) {
            return;
        }
        
        // Screenshot image (top)
        Glide.with(screenshotView.getContext())
                .load(game.getScreenshotWithFallback())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(screenshotView);

        // Box2D image (bottom) - icône avec fallback
        Glide.with(box2dView.getContext())
                .load(game.getImageWithFallback())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(box2dView);
    }
}
