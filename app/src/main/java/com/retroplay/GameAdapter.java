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
        
        // Set game data with database enrichment hint
        holder.title.setText(game.getTitle());
        
        // Genre - Show DB icon if we expect it's in database
        String genreText = game.getGenre();
        // Note: We don't calculate CRC here (too slow for RecyclerView)
        // Database info will be shown in GameDetailsActivity
        holder.genreChip.setText(genreText);
        
        holder.playersInfo.setText("👥 " + game.getPlayers() + "P");
        holder.releaseYear.setText(extractYear(game.getReleasedate()));
        
        // Description - Add hint that database info available in details
        String desc = game.getDesc();
        if (desc != null && desc.length() > 100) {
            desc = desc.substring(0, 100) + "...";
        }
        desc += "\n💾 DB info in details";
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
        
        // Apply theme to item
        applyTheme(holder);
        
        // Add entrance animation
        Animation slideIn = AnimationUtils.loadAnimation(holder.itemView.getContext(), android.R.anim.slide_in_left);
        slideIn.setStartOffset(position * 100); // Stagger animation
        holder.itemView.startAnimation(slideIn);
    }
    
    private void applyTheme(ViewHolder holder) {
        android.content.Context context = holder.itemView.getContext();
        ThemeManager themeManager = ThemeManager.getInstance(context);
        int primaryColor = themeManager.getPrimaryColor(context);
        int mediumColor = themeManager.getMediumColor(context);
        int textPrimaryColor = themeManager.getTextPrimaryColor(context);
        int textSecondaryColor = themeManager.getTextSecondaryColor(context);
        
        // Card stroke and background
        if (holder.itemView instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.itemView;
            card.setStrokeColor(primaryColor);
            card.setCardBackgroundColor(mediumColor);
        }
        
        // Title
        holder.title.setTextColor(primaryColor);
        
        // Info texts (players, year, genre)
        holder.playersInfo.setTextColor(textSecondaryColor);
        holder.releaseYear.setTextColor(textSecondaryColor);
        holder.genreChip.setTextColor(textSecondaryColor);
        
        // Description
        holder.description.setTextColor(textSecondaryColor);
        
        // Play button
        holder.playButton.setBackgroundTint(android.content.res.ColorStateList.valueOf(primaryColor));
        holder.playButton.setIconTint(android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.kitt_black)));
        
        // Favorite button
        holder.favoriteButton.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        holder.favoriteButton.setBackgroundTint(android.content.res.ColorStateList.valueOf(mediumColor));
        holder.favoriteButton.setStrokeColor(android.content.res.ColorStateList.valueOf(primaryColor));
        
        // Loading progress
        holder.loadingProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(primaryColor));
    }
    
    private String extractYear(String releaseDate) {
        try {
            if (releaseDate.length() >= 4) {
                return releaseDate.substring(0, 4);
            }
        } catch (Exception e) {
            // Ignore
        }
        return "1988";
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
