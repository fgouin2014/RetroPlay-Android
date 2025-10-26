package com.retroplay;

/**
 * Modèle pour stocker un override de core pour un jeu spécifique
 * Permet de forcer l'utilisation d'un core différent du core par défaut de la console
 * 
 * Exemple: afighter.zip dans fbneo/sega/ peut forcer l'utilisation de mame2010 au lieu de fbneo
 */
public class CoreOverride {
    private String gamePath;      // Chemin relatif: "fbneo/sega/afighter.zip"
    private String coreId;        // Core à utiliser: "mame2010", "fbneo", "mame2003_plus"
    private String reason;        // Raison optionnelle: "Missing .key file", "Better compatibility"
    
    public CoreOverride() {
    }
    
    public CoreOverride(String gamePath, String coreId) {
        this.gamePath = gamePath;
        this.coreId = coreId;
    }
    
    public CoreOverride(String gamePath, String coreId, String reason) {
        this.gamePath = gamePath;
        this.coreId = coreId;
        this.reason = reason;
    }
    
    // Getters et setters
    public String getGamePath() {
        return gamePath;
    }
    
    public void setGamePath(String gamePath) {
        this.gamePath = gamePath;
    }
    
    public String getCoreId() {
        return coreId;
    }
    
    public void setCoreId(String coreId) {
        this.coreId = coreId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    @Override
    public String toString() {
        return "CoreOverride{" +
                "gamePath='" + gamePath + '\'' +
                ", coreId='" + coreId + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}

