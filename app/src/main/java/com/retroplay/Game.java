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
    
    // Chemins vers les images (calculés dynamiquement)
    public String imagePath;
    public String screenshotPath;
    public String consoleId = "nes"; // Console par défaut

    public Game(String id, String name, String path, String desc, String releasedate, String genre, String players) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.desc = desc;
        this.releasedate = releasedate;
        this.genre = genre;
        this.players = players;
        
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
    
    public String getConsole() {
        return consoleId;
    }
    
    public void setConsole(String consoleId) {
        this.consoleId = consoleId;
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

    public String getFile() {
        String fileName = path.substring(2); // Enlever "./" du début
        // Utiliser le WebServer au lieu de file:// pour éviter les restrictions de sécurité
        String filePath = "http://localhost:7777/gamedata/" + consoleId + "/" + fileName;
        
        // Debug: afficher le chemin du fichier
        System.out.println("Game file path: " + filePath + " (console: " + consoleId + ")");
        
        return filePath;
    }
    
    /**
     * Initialise les chemins vers les images en utilisant ObbManager
     */
    public void initializePaths(ObbManager obbManager) {
        String baseName = getBaseNameFromPath(path);
        // Utiliser les noms de fichiers tels quels (sans encodage URL)
        this.imagePath = "http://localhost:7777/gamedata/nes/media/box2d/" + baseName + ".png";
        this.screenshotPath = "http://localhost:7777/gamedata/nes/media/screenshot/" + baseName + ".png";
        
        // Debug: afficher les chemins générés
        System.out.println("Game: " + name);
        System.out.println("Image path: " + this.imagePath);
        System.out.println("Screenshot path: " + this.screenshotPath);
    }
    
    /**
     * Retourne l'URL HTTP de l'image (Glide gère automatiquement le fallback)
     */
    public String getImageWithFallback() {
        String baseName = getBaseNameFromPath(path);
        // Encoder le baseName pour gérer les caractères spéciaux (+, &, %, etc.)
        String encodedName = android.net.Uri.encode(baseName);
        return "http://localhost:7777/gamedata/" + consoleId + "/media/box2d/" + encodedName + ".png";
    }
    
    /**
     * Retourne l'URL HTTP de la screenshot (Glide gère automatiquement le fallback)
     */
    public String getScreenshotWithFallback() {
        String baseName = getBaseNameFromPath(path);
        // Encoder le baseName pour gérer les caractères spéciaux (+, &, %, etc.)
        String encodedName = android.net.Uri.encode(baseName);
        return "http://localhost:7777/gamedata/" + consoleId + "/media/screenshot/" + encodedName + ".png";
    }
}

