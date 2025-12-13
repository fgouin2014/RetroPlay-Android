package com.retroplay.helpers;

import android.util.Log;
import com.retroplay.Game;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper pour résoudre et formater les métadonnées de jeu
 * Extrait de GameDetailsActivity pour améliorer la modularité
 */
public class MetadataResolver {
    private static final String TAG = "MetadataResolver";
    
    /**
     * Résout et formate la description du jeu
     */
    public static String resolveDescription(Game game) {
        if (game == null) {
            return "No description available";
        }
        
        String description = (game.getDesc() != null && !game.getDesc().isEmpty())
                ? game.getDesc()
                : "No description available";
        
        // Ajouter hash si disponible
        if (game.getHash() != null) {
            description += "\n\n💾 Hash: " + game.getHash();
        }
        
        return description;
    }
    
    /**
     * Résout le genre du jeu
     */
    public static String resolveGenre(Game game) {
        if (game == null) {
            return "🎭 Unknown";
        }
        
        return (game.getGenre() != null && !game.getGenre().isEmpty())
                ? "🎭 " + game.getGenre()
                : "🎭 Unknown";
    }
    
    /**
     * Résout le nombre de joueurs
     */
    public static String resolvePlayers(Game game) {
        if (game == null) {
            return "👥 1";
        }
        
        String playersText = "👥 ";
        if (game.getPlayers() != null && !game.getPlayers().isEmpty()) {
            playersText += game.getPlayers();
        } else {
            playersText += "1";
        }
        return playersText;
    }
    
    /**
     * Résout et formate la date de sortie avec développeur et éditeur
     */
    public static String resolveReleaseDate(Game game) {
        if (game == null) {
            return formatReleaseDate(null);
        }
        
        String releaseDate = formatReleaseDate(game.getReleasedate());
        
        if (game.getDeveloper() != null && !game.getDeveloper().isEmpty()) {
            releaseDate += " • 🏢 " + game.getDeveloper();
        }
        
        if (game.getPublisher() != null && !game.getPublisher().isEmpty() &&
                !game.getPublisher().equals(game.getDeveloper())) {
            releaseDate += " • 📦 " + game.getPublisher();
        }
        
        return releaseDate;
    }
    
    /**
     * Formate une date de sortie
     */
    private static String formatReleaseDate(String releaseDate) {
        if (releaseDate == null || releaseDate.isEmpty()) {
            return "📅 Unknown";
        }
        
        // Essayer de parser différentes formats de date
        String[] formats = {
            "yyyy-MM-dd",
            "yyyyMMdd",
            "yyyy",
            "MM/dd/yyyy",
            "dd/MM/yyyy"
        };
        
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                Date date = sdf.parse(releaseDate);
                if (date != null) {
                    // Formater en format lisible
                    SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
                    return "📅 " + outputFormat.format(date);
                }
            } catch (ParseException e) {
                // Essayer le format suivant
            }
        }
        
        // Si aucun format ne fonctionne, retourner tel quel
        return "📅 " + releaseDate;
    }
    
    /**
     * Résout le rating du jeu
     */
    public static String resolveRating(Game game) {
        if (game == null || game.getRating() == null || game.getRating().isEmpty()) {
            return null;
        }
        return "⭐ " + game.getRating();
    }
}
