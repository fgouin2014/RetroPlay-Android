package com.retroplay.managers;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Manager pour gérer le mode plein écran
 * Extrait de GameListActivity pour réutilisabilité
 */
public class FullscreenManager {
    private static final String TAG = "FullscreenManager";
    
    private static FullscreenManager instance;
    
    private FullscreenManager() {
        // Singleton
    }
    
    public static FullscreenManager getInstance() {
        if (instance == null) {
            instance = new FullscreenManager();
        }
        return instance;
    }
    
    /**
     * Configure le mode plein écran pour une Activity
     * Masque la barre d'état et la barre de navigation
     * 
     * @param activity L'Activity à configurer
     */
    public void setupFullscreenMode(Activity activity) {
        if (activity == null) {
            Log.w(TAG, "setupFullscreenMode: activity is null");
            return;
        }
        
        Window window = activity.getWindow();
        if (window == null) {
            Log.w(TAG, "setupFullscreenMode: window is null");
            return;
        }
        
        // Masquer la barre d'état et la navigation (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsControllerCompat controller = 
                new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            // Fallback pour API < 30
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
        
        // Garder l'écran allumé
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        Log.d(TAG, "Fullscreen mode configured for API level " + Build.VERSION.SDK_INT);
    }
    
    /**
     * Affiche les barres système (barre d'état et navigation)
     * 
     * @param activity L'Activity concernée
     */
    public void showSystemBars(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        
        Window window = activity.getWindow();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat controller = 
                new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.show(WindowInsetsCompat.Type.systemBars());
        } else {
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }
    
    /**
     * Retire le flag KEEP_SCREEN_ON
     * 
     * @param activity L'Activity concernée
     */
    public void disableKeepScreenOn(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}

