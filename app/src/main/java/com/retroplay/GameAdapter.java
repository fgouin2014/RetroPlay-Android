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

    public interface OnGameClickListener {
        void onClick(Game game);
    }

    public GameAdapter(List<Game> games, OnGameClickListener listener) {
        this.games = games;
        this.listener = listener;
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
        
        // Set game data
        holder.title.setText(game.getTitle());
        holder.genreChip.setText(game.getGenre());
        holder.playersInfo.setText("👥 " + game.getPlayers() + "P");
        holder.releaseYear.setText(extractYear(game.getReleasedate()));
        holder.description.setText(game.getDesc());
        
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
            holder.favoriteButton.setOnClickListener(v -> {
                // TODO: Implement favorite functionality
                toggleFavorite(holder, game);
            });
        }
        
        // Add entrance animation
        Animation slideIn = AnimationUtils.loadAnimation(holder.itemView.getContext(), android.R.anim.slide_in_left);
        slideIn.setStartOffset(position * 100); // Stagger animation
        holder.itemView.startAnimation(slideIn);
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
        // TODO: Implement favorite persistence
        // For now, just toggle the text
        String currentText = holder.favoriteButton.getText().toString();
        if ("❤".equals(currentText)) {
            holder.favoriteButton.setText("🤍");
        } else {
            holder.favoriteButton.setText("❤");
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
