package com.retroplay.helpers;

/**
 * Helper pour la gestion des fichiers ROM.
 * Extracted from ConsoleManagerActivity to improve modularity and reusability.
 */
public class RomFileHelper {
    
    /**
     * Vérifie si un fichier est une ROM valide selon son extension
     */
    public static boolean isRomFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".nes") || lowerName.endsWith(".smc") || lowerName.endsWith(".sfc") ||
               lowerName.endsWith(".z64") || lowerName.endsWith(".n64") || lowerName.endsWith(".v64") ||
               lowerName.endsWith(".bin") || lowerName.endsWith(".md") || lowerName.endsWith(".gen") ||
               lowerName.endsWith(".smd") || lowerName.endsWith(".gba") || lowerName.endsWith(".gb") ||
               lowerName.endsWith(".gbc") || lowerName.endsWith(".nds") || lowerName.endsWith(".pbp") ||
               lowerName.endsWith(".iso") || lowerName.endsWith(".cue") || lowerName.endsWith(".img") ||
               lowerName.endsWith(".cso") || lowerName.endsWith(".zip") || lowerName.endsWith(".7z") ||
               lowerName.endsWith(".rar") || lowerName.endsWith(".chd");
    }
    
    /**
     * Extraire le nom de base d'un fichier ROM (sans extension)
     */
    public static String getBaseNameFromFile(String fileName) {
        String baseName = fileName;
        
        // Enlever les extensions connues
        String[] extensions = {".nes", ".smc", ".sfc", ".z64", ".n64", ".v64", ".bin", ".md", 
                               ".gen", ".smd", ".gba", ".gb", ".gbc", ".nds", ".pbp", ".iso", 
                               ".cue", ".img", ".cso", ".zip", ".7z", ".rar", ".chd"};
        
        for (String ext : extensions) {
            if (baseName.toLowerCase().endsWith(ext)) {
                baseName = baseName.substring(0, baseName.length() - ext.length());
                break;
            }
        }
        
        return baseName;
    }
}
