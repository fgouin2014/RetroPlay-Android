package com.retroplay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import java.util.List;

/**
 * Adapter pour afficher la liste des jeux dans une RecyclerView
 */
public class GameAdapter extends RecyclerView.Adapter<GameAdapter.ViewHolder> {
    private List<Game> games;
    private OnGameClickListener listener;
    private FavoritesManager favoritesManager;

    public interface OnGameClickListener {
        void onClick(Game game);
    }

    public GameAdapter(List<Game> games, OnGameClickListener listener) {
        this.games = games;
        this.listener = listener;
    }
    
    public void setFavoritesManager(FavoritesManager manager) {
        this.favoritesManager = manager;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game_modern, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Game game = games.get(position);
        
        // Set game data from gamelist.json (scrapped metadata)
        holder.title.setText(game.getTitle());
        
        // Genre - From gamelist.json (scrapped from RetroArch database)
        String genreText = (game.getGenre() != null && !game.getGenre().isEmpty()) 
            ? game.getGenre() 
            : "Unknown";
        holder.genreChip.setText(genreText);
        
        // Players - From gamelist.json
        String playersText = (game.getPlayers() != null && !game.getPlayers().isEmpty()) 
            ? "👥 " + game.getPlayers() 
            : "👥 1";
        holder.playersInfo.setText(playersText);
        
        // Release year - From gamelist.json
        String year = extractYear(game.getReleasedate());
        holder.releaseYear.setText(year);
        
        // Description - From gamelist.json (scrapped from RetroArch database)
        String desc = (game.getDesc() != null && !game.getDesc().isEmpty()) 
            ? game.getDesc() 
            : "No description available";
        if (desc.length() > 100) {
            desc = desc.substring(0, 100) + "...";
        }
        holder.description.setText(desc);
        
        // Show loading progress
        holder.loadingProgress.setVisibility(View.VISIBLE);
        
        // Load game image with animation
        Glide.with(holder.itemView.getContext())
            .load(game.getImageWithFallback())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                @Override
                public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                    holder.loadingProgress.setVisibility(View.GONE);
                    return false;
                }
                
                @Override
                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                    holder.loadingProgress.setVisibility(View.GONE);
                    // Add fade-in animation
                    Animation fadeIn = AnimationUtils.loadAnimation(holder.itemView.getContext(), android.R.anim.fade_in);
                    holder.image.startAnimation(fadeIn);
                    return false;
                }
            })
            .into(holder.image);
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(game);
            }
        });
        
        holder.playButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(game);
            }
        });
        
        if (holder.favoriteButton != null) {
            // Mettre à jour l'état du bouton favori
            updateFavoriteButton(holder.favoriteButton, game);
            
            holder.favoriteButton.setOnClickListener(v -> {
                toggleFavorite(holder, game);
            });
        }
        
        // Check if game has saves and show indicator
        checkAndShowSaveIndicator(holder, game);
        
        // Check if game is favorite and show indicator
        checkAndShowFavoriteIndicator(holder, game);
        
        // Apply theme to item
        applyTheme(holder);
        
        // Add entrance animation
        Animation slideIn = AnimationUtils.loadAnimation(holder.itemView.getContext(), android.R.anim.slide_in_left);
        slideIn.setStartOffset(position * 100); // Stagger animation
        holder.itemView.startAnimation(slideIn);
    }
    
    /**
     * Vérifie si le jeu a des sauvegardes et affiche l'indicateur
     */
    private void checkAndShowSaveIndicator(ViewHolder holder, Game game) {
        if (holder.saveIndicator == null) return;
        
        String console = game.getConsole();
        String gameName = game.getName();
        
        // Vérifier si au moins un slot contient une sauvegarde
        boolean hasSave = false;
        for (int slot = 1; slot <= 5; slot++) {
            java.io.File saveFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/saves/" + console + "/slot" + slot + "/" + gameName + ".state");
            if (saveFile.exists()) {
                hasSave = true;
                break;
            }
        }
        
        if (hasSave) {
            holder.saveIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.saveIndicator.setVisibility(View.GONE);
        }
    }
    
    /**
     * Vérifie si le jeu est favori et affiche l'indicateur
     */
    private void checkAndShowFavoriteIndicator(ViewHolder holder, Game game) {
        if (holder.favoriteIndicator == null) return;
        
        if (favoritesManager != null && favoritesManager.isFavorite(game)) {
            holder.favoriteIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.favoriteIndicator.setVisibility(View.GONE);
        }
    }
    
    private void applyTheme(ViewHolder holder) {
        android.content.Context context = holder.itemView.getContext();
        ThemeManager themeManager = ThemeManager.getInstance(context);
        int primaryColor = themeManager.getPrimaryColor(context);
        int headerBackgroundColor = themeManager.getHeaderBackgroundColor(context);
        int textPrimaryColor = themeManager.getTextPrimaryColor(context);
        int textSecondaryColor = themeManager.getTextSecondaryColor(context);
        float density = context.getResources().getDisplayMetrics().density;
        
        // Card stroke and background
        if (holder.itemView instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.itemView;
            card.setStrokeColor(primaryColor);
            card.setCardBackgroundColor(headerBackgroundColor);
        }
        
        // Title
        holder.title.setTextColor(primaryColor);
        holder.title.setAlpha(1.0f);
        
        // Info texts (players, year, genre)
        holder.playersInfo.setTextColor(textSecondaryColor);
        holder.playersInfo.setAlpha(1.0f);
        holder.releaseYear.setTextColor(textSecondaryColor);
        holder.releaseYear.setAlpha(1.0f);
        holder.genreChip.setTextColor(textSecondaryColor);
        holder.genreChip.setAlpha(1.0f);
        
        // Description
        holder.description.setTextColor(textSecondaryColor);
        holder.description.setAlpha(1.0f);
        
        // Game Image border (create drawable programmatically with theme colors)
        android.graphics.drawable.GradientDrawable imageDrawable = new android.graphics.drawable.GradientDrawable();
        imageDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        imageDrawable.setCornerRadius(6 * density); // 6dp
        imageDrawable.setColor(headerBackgroundColor); // Background color
        imageDrawable.setStroke((int)(2 * density), primaryColor); // 2dp stroke with theme color
        holder.image.setBackground(imageDrawable);
        holder.image.setAlpha(1.0f);
        
        // Save indicator overlay (if visible)
        if (holder.saveIndicator != null && holder.saveIndicator.getVisibility() == View.VISIBLE) {
            holder.saveIndicator.setImageResource(R.drawable.ic_save_24);
            holder.saveIndicator.setColorFilter(primaryColor);
            holder.saveIndicator.setAlpha(1.0f);
            
            // Ajouter un fond circulaire semi-transparent pour meilleure visibilité
            android.graphics.drawable.GradientDrawable indicatorBg = new android.graphics.drawable.GradientDrawable();
            indicatorBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            // Créer une couleur avec alpha (200/255 = ~78% d'opacité)
            int bgColorWithAlpha = android.graphics.Color.argb(200, 
                android.graphics.Color.red(headerBackgroundColor),
                android.graphics.Color.green(headerBackgroundColor),
                android.graphics.Color.blue(headerBackgroundColor));
            indicatorBg.setColor(bgColorWithAlpha);
            holder.saveIndicator.setBackground(indicatorBg);
        }
        
        // Favorite indicator overlay (if visible)
        if (holder.favoriteIndicator != null && holder.favoriteIndicator.getVisibility() == View.VISIBLE) {
            holder.favoriteIndicator.setImageResource(R.drawable.ic_favorite_24);
            holder.favoriteIndicator.setColorFilter(primaryColor);
            holder.favoriteIndicator.setAlpha(1.0f);
            
            // Ajouter un fond circulaire semi-transparent pour meilleure visibilité
            android.graphics.drawable.GradientDrawable favoriteIndicatorBg = new android.graphics.drawable.GradientDrawable();
            favoriteIndicatorBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            // Créer une couleur avec alpha (200/255 = ~78% d'opacité)
            int bgColorWithAlpha = android.graphics.Color.argb(200, 
                android.graphics.Color.red(headerBackgroundColor),
                android.graphics.Color.green(headerBackgroundColor),
                android.graphics.Color.blue(headerBackgroundColor));
            favoriteIndicatorBg.setColor(bgColorWithAlpha);
            holder.favoriteIndicator.setBackground(favoriteIndicatorBg);
        }
        
        // Play button
        holder.playButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
        holder.playButton.setIconTint(android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.kitt_black)));
        holder.playButton.setAlpha(1.0f);
        
        // Favorite button
        holder.favoriteButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        holder.favoriteButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(headerBackgroundColor));
        holder.favoriteButton.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
        holder.favoriteButton.setAlpha(1.0f);
        
        // Loading progress
        holder.loadingProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(primaryColor));
    }
    
    private String extractYear(String releaseDate) {
        try {
            if (releaseDate != null && !releaseDate.isEmpty()) {
                // Support format YYYY-MM-DD ou YYYY
                if (releaseDate.length() >= 4) {
                    return releaseDate.substring(0, 4);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }
    
    private void toggleFavorite(ViewHolder holder, Game game) {
        if (favoritesManager == null) {
            return;
        }
        
        // Toggle dans le manager et mettre à jour l'état du jeu
        boolean isFavorite = favoritesManager.toggleFavorite(game);
        game.setFavorite(isFavorite);
        
        // Mettre à jour l'UI
        updateFavoriteButton(holder.favoriteButton, game);
        
        // Mettre à jour l'indicateur overlay
        checkAndShowFavoriteIndicator(holder, game);
        
        // Réappliquer le thème pour mettre à jour le style de l'indicateur
        applyTheme(holder);
    }
    
    private void updateFavoriteButton(MaterialButton button, Game game) {
        if (favoritesManager != null && favoritesManager.isFavorite(game)) {
            button.setIconResource(R.drawable.ic_favorite_24);
            game.setFavorite(true);
        } else {
            button.setIconResource(R.drawable.ic_favorite_border_24);
            game.setFavorite(false);
        }
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView image;
        ImageView saveIndicator;
        ImageView favoriteIndicator;
        TextView genreChip;
        TextView playersInfo;
        TextView releaseYear;
        TextView description;
        MaterialButton playButton;
        MaterialButton favoriteButton;
        ProgressBar loadingProgress;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            image = itemView.findViewById(R.id.image);
            saveIndicator = itemView.findViewById(R.id.save_indicator);
            favoriteIndicator = itemView.findViewById(R.id.favorite_indicator);
            genreChip = itemView.findViewById(R.id.genre_chip);
            playersInfo = itemView.findViewById(R.id.players_info);
            releaseYear = itemView.findViewById(R.id.release_year);
            description = itemView.findViewById(R.id.description);
            playButton = itemView.findViewById(R.id.play_button);
            favoriteButton = itemView.findViewById(R.id.favorite_button);
            loadingProgress = itemView.findViewById(R.id.loading_progress);
        }
    }
}
