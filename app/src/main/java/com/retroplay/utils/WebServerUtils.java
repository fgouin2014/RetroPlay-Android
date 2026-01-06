package com.retroplay.utils;

/**
 * Utility class for WebServer operations.
 * Extracted from WebServer.java to improve modularity and testability.
 */
public class WebServerUtils {
    
    /**
     * Extrait le nom de base d'un fichier (sans extension)
     * 
     * @param fileName Le nom complet du fichier
     * @return Le nom sans extension
     */
    public static String getBaseNameFromFile(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
    
    /**
     * Normalise le nom pour l'affichage dans le gamelist.json
     * Exemple: "Game (U) (V1.2) [!]" → "Game (USA)"
     * 
     * @param romName Le nom original de la ROM
     * @return Le nom normalisé
     */
    public static String normalizeDisplayName(String romName) {
        String normalized = romName;
        
        // Remplacer les codes région courts par les noms complets
        normalized = normalized.replaceAll("\\(U\\)", "(USA)");
        normalized = normalized.replaceAll("\\(E\\)", "(Europe)");
        normalized = normalized.replaceAll("\\(J\\)", "(Japan)");
        normalized = normalized.replaceAll("\\(W\\)", "(World)");
        normalized = normalized.replaceAll("\\(UE\\)", "(USA, Europe)");
        normalized = normalized.replaceAll("\\(JU\\)", "(Japan, USA)");
        
        // Enlever tout après la première région: (V...), [!], [h...], etc.
        // Trouve la première parenthèse fermée (fin de région)
        int firstCloseParen = normalized.indexOf(')');
        if (firstCloseParen > 0 && firstCloseParen < normalized.length() - 1) {
            String afterRegion = normalized.substring(firstCloseParen + 1);
            // Si ce qui suit commence par " (" ou " [", on coupe
            if (afterRegion.startsWith(" (") || afterRegion.startsWith(" [")) {
                normalized = normalized.substring(0, firstCloseParen + 1);
            }
        }
        
        // Enlever les tags entre crochets qui restent
        normalized = normalized.replaceAll("\\s*\\[.*?\\]", "");
        
        return normalized.trim();
    }
    
    /**
     * Détermine le type MIME d'un fichier basé sur son extension
     * 
     * @param fileName Le nom du fichier
     * @return Le type MIME correspondant
     */
    public static String getMimeType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        switch (extension) {
            case "html":
            case "htm":
                return "text/html; charset=utf-8";
            case "css":
                return "text/css; charset=utf-8";
            case "js":
                return "application/javascript; charset=utf-8";
            case "json":
                return "application/json; charset=utf-8";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            case "webp":
                return "image/webp";
            case "ico":
                return "image/x-icon";
            case "txt":
                return "text/plain; charset=utf-8";
            default:
                return "application/octet-stream";
        }
    }
    
    /**
     * Formate la taille d'un fichier en format lisible
     * 
     * @param bytes La taille en bytes
     * @return La taille formatée (B, KB, MB, GB)
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Détermine le Content-Type d'un fichier (version simplifiée de getMimeType)
     * 
     * @param filename Le nom du fichier
     * @return Le Content-Type correspondant
     */
    public static String getContentType(String filename) {
        if (filename.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (filename.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        } else if (filename.endsWith(".json")) {
            return "application/json; charset=utf-8";
        } else if (filename.endsWith(".nes")) {
            return "application/octet-stream";
        } else if (filename.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream";
        }
    }
}

