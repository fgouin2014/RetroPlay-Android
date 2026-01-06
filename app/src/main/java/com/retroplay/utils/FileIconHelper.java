package com.retroplay.utils;

/**
 * Helper class for file icon and type operations.
 * Extracted from WebServer.java to improve modularity and testability.
 */
public class FileIconHelper {
    
    /**
     * Retourne l'icône (emoji) associée à un type de fichier
     * 
     * @param fileName Le nom du fichier
     * @return L'emoji correspondant au type de fichier
     */
    public static String getFileIcon(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "nes": return "\uD83C\uDFAE"; // 🎮
            case "rom": return "\uD83C\uDFAE"; // 🎮
            case "zip": return "\uD83D\uDCE6"; // 📦
            case "js": return "\uD83D\uDCDC"; // 📜
            case "html": return "\uD83C\uDF10"; // 🌐
            case "css": return "\uD83C\uDFA8"; // 🎨
            case "json": return "\uD83D\uDCCB"; // 📋
            case "png": 
            case "jpg": 
            case "jpeg": 
            case "gif": return "\uD83D\uDDBC"; // 🖼️
            case "txt": return "\uD83D\uDCC4"; // 📄
            case "md": return "\uD83D\uDCDD"; // 📝
            default: return "\uD83D\uDCC4"; // 📄
        }
    }
    
    /**
     * Retourne le type de fichier selon l'extension
     * 
     * @param fileName Le nom du fichier
     * @return Le type de fichier (ex: "NES Game", "Archive")
     */
    public static String getFileType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "nes": return "NES Game";
            case "rom": return "ROM File";
            case "zip": return "Archive";
            case "js": return "JavaScript";
            case "html": return "Web Page";
            case "css": return "Stylesheet";
            case "json": return "JSON Data";
            case "png": 
            case "jpg": 
            case "jpeg": 
            case "gif": return "Image";
            case "txt": return "Text File";
            case "md": return "Markdown";
            default: return "File";
        }
    }
}

