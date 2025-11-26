package com.retroplay;

/**
 * Modèle de données pour représenter un jeu
 */
public class Game implements java.io.Serializable {
    public String id;
    public String name;
    public String path;
    public String desc;
    public String releasedate;
    public String genre;
    public String players;
    
    // Champs ES (EmulationStation style)
    public String developer;
    public String publisher;
    public String rating;
    public String hash;  // Hash principal (SHA1, MD5 ou CRC32)
    
    // Chemins vers les images (calculés dynamiquement)
    public String imagePath;
    public String screenshotPath;
    public String consoleId = "nes"; // Console par défaut
    
    // État favori (non sérialisé, recalculé à chaque fois)
    private transient boolean isFavorite = false;

    public Game(String id, String name, String path, String desc, String releasedate, String genre, String players) {
        this(id, name, path, desc, releasedate, genre, players, null, null, null, null);
    }
    
    public Game(String id, String name, String path, String desc, String releasedate, String genre, String players,
                String developer, String publisher, String rating, String hash) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.desc = desc;
        this.releasedate = releasedate;
        this.genre = genre;
        this.players = players;
        this.developer = developer;
        this.publisher = publisher;
        this.rating = rating;
        this.hash = hash;
        
        // Les chemins vers les images seront calculés dynamiquement par ObbManager
        this.imagePath = null;
        this.screenshotPath = null;
    }
    
    private String getBaseNameFromPath(String path) {
        // Nettoyer le path: enlever "./" au début
        String cleanPath = path;
        if (cleanPath.startsWith("./")) {
            cleanPath = cleanPath.substring(2);
        }
        
        // Extraire le nom du fichier sans extension
        String fileName = cleanPath.substring(Math.max(0, cleanPath.lastIndexOf("/") + 1));
        
        // Retirer toutes les extensions possibles (.zip, .z64, .n64, .smc, .sfc, etc.)
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    /**
     * Génère un slug URL-friendly à partir du nom du jeu
     * Ex: "1943 : The Battle of Midway" → "1943-the-battle-of-midway"
     */
    public String generateSlug() {
        if (name == null) return "";
        
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")  // Garder seulement lettres, chiffres, espaces et tirets
                .replaceAll("\\s+", "-")          // Remplacer espaces par tirets
                .replaceAll("-+", "-")            // Éliminer tirets multiples
                .replaceAll("^-|-$", "");         // Éliminer tirets en début/fin
    }

    public String getPath() {
        return path;
    }

    public String getDesc() {
        return desc;
    }

    public String getReleasedate() {
        return releasedate;
    }

    public String getGenre() {
        return genre;
    }

    public String getPlayers() {
        return players;
    }
    
    public String getDeveloper() {
        return developer;
    }
    
    public String getPublisher() {
        return publisher;
    }
    
    public String getRating() {
        return rating;
    }
    
    public String getHash() {
        return hash;
    }
    
    public String getConsole() {
        return consoleId;
    }
    
    public void setConsole(String consoleId) {
        this.consoleId = consoleId;
    }
    
    public boolean isFavorite() {
        return isFavorite;
    }
    
    public void setFavorite(boolean favorite) {
        this.isFavorite = favorite;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getScreenshotPath() {
        return screenshotPath;
    }
    
    // Méthodes de compatibilité pour l'ancien code
    public String getTitle() {
        return name;
    }

    public String getImage() {
        return imagePath;
    }

    public String getScreenshot() {
        return screenshotPath;
    }

    /**
     * Retourne le chemin local du fichier ROM
     * Format: /storage/emulated/0/GameLibrary-Data/{console}/{filename}
     */
    public String getFile() {
        // Nettoyer le chemin (enlever "./" si présent)
        String cleanPath = path.startsWith("./") ? path.substring(2) : path;
        
        // Construire le chemin local complet
        String localPath = "/storage/emulated/0/GameLibrary-Data/" + consoleId + "/" + cleanPath;
        
        return localPath;
    }
    
    /**
     * Retourne l'URL HTTP pour le WebServer (pour EmulatorJS/WebView)
     * Format: http://localhost:7777/gamedata/{console}/{filename}
     */
    public String getFileUrl() {
        String fileName = path.startsWith("./") ? path.substring(2) : path;
        return "http://localhost:7777/gamedata/" + consoleId + "/" + fileName;
    }
    
    /**
     * Retourne le nom du fichier ROM sans extension (pour compatibilité playlists/RetroArch)
     */
    public String getBaseName() {
        return getBaseNameFromPath(path);
    }
    
    /**
     * Initialise les chemins vers les images en utilisant ObbManager
     * Utilise maintenant des chemins locaux au lieu d'URLs HTTP
     */
    public void initializePaths(ObbManager obbManager) {
        String baseName = getBaseNameFromPath(path);
        // Utiliser consoleId au lieu de "nes" hardcodé
        // Chemins locaux pour les images
        this.imagePath = "/storage/emulated/0/GameLibrary-Data/" + consoleId + "/media/box2d/" + baseName + ".png";
        this.screenshotPath = "/storage/emulated/0/GameLibrary-Data/" + consoleId + "/media/screenshot/" + baseName + ".png";
        
        // Debug: afficher les chemins générés
        System.out.println("Game: " + name);
        System.out.println("Image path: " + this.imagePath);
        System.out.println("Screenshot path: " + this.screenshotPath);
    }
    
    /**
     * Retourne l'URL ou le fichier de l'image principale avec fallback intelligent
     */
    public String getImageWithFallback() {
        return ArtworkResolver.resolveBoxArt(this);
    }
    
    /**
     * Retourne l'URL ou le fichier de la capture d'écran avec fallback intelligent
     */
    public String getScreenshotWithFallback() {
        return ArtworkResolver.resolveScreenshot(this);
    }
}

