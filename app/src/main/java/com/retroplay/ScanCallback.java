package com.retroplay;

/**
 * Interface pour les callbacks de scan depuis Java
 */
public interface ScanCallback {
    void onComplete(GamelistScanner.ScanResult result);
    void onError(String error);
    /**
     * Callback de progression pendant le scan
     * @param consoleName Nom de la console en cours de scan (peut être null)
     * @param current Numéro de la console actuelle (0-based)
     * @param total Nombre total de consoles à scanner
     * @param gamesFound Nombre de jeux trouvés dans la console actuelle
     */
    default void onProgress(String consoleName, int current, int total, int gamesFound) {
        // Par défaut, ne rien faire (pour compatibilité)
    }
}

