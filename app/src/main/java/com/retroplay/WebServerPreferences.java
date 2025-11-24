package com.retroplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Gestion des préférences pour le démarrage automatique du WebServer
 * Permet de contrôler quand le serveur doit être démarré :
 * - Automatiquement au démarrage de l'app (désactivé par défaut)
 * - Lors du lancement d'un jeu WASM (toujours activé)
 * - Quand l'utilisateur active l'option "servir sur réseau" (optionnel)
 */
public class WebServerPreferences {
    private static final String TAG = "WebServerPreferences";
    private static final String PREFS_NAME = "webserver_prefs";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_NETWORK_SERVER = "network_server";
    
    private final SharedPreferences prefs;
    
    private static WebServerPreferences instance;
    
    private WebServerPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized WebServerPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new WebServerPreferences(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Retourne true si le serveur doit démarrer automatiquement au lancement de l'app
     * Par défaut: false (le serveur démarre uniquement quand nécessaire)
     */
    public boolean shouldAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, false);
    }
    
    /**
     * Active/désactive le démarrage automatique au lancement de l'app
     */
    public void setAutoStart(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply();
        Log.i(TAG, "Auto-start WebServer: " + enabled);
    }
    
    /**
     * Retourne true si l'utilisateur veut servir la bibliothèque sur le réseau
     * Par défaut: false
     */
    public boolean isNetworkServerEnabled() {
        return prefs.getBoolean(KEY_NETWORK_SERVER, false);
    }
    
    /**
     * Active/désactive l'option "servir sur réseau"
     * Quand activé, le serveur reste actif même après fermeture des jeux WASM
     */
    public void setNetworkServerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NETWORK_SERVER, enabled).apply();
        Log.i(TAG, "Network server mode: " + enabled);
    }
    
    /**
     * Retourne true si le serveur doit être démarré maintenant
     * Utilisé pour décider si on doit démarrer le serveur au démarrage de l'app
     */
    public boolean shouldStartServer() {
        return shouldAutoStart() || isNetworkServerEnabled();
    }
    
    /**
     * Retourne true si le serveur doit continuer à tourner après fermeture d'un jeu WASM
     */
    public boolean shouldKeepServerRunning() {
        return isNetworkServerEnabled();
    }
}

