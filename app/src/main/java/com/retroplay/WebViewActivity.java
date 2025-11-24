package com.retroplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity pour afficher EmulatorJS dans une WebView
 */
public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private static final int WEBSERVER_PORT = 7777;
    private static final String TAG = "WebViewActivity";
    
    private boolean serverStartedByThisActivity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Démarrer le serveur web si nécessaire (pour les jeux WASM)
        ensureWebServerRunning();
        
        // Mode plein écran - masquer barre d'état et navigation
        setupFullscreenMode();
        
        setContentView(R.layout.activity_webview);

        String gameFile = getIntent().getStringExtra("file");
        if (gameFile == null) {
            gameFile = "games/mario.zip"; // Fichier par défaut
        }
        
        String console = getIntent().getStringExtra("console");
        if (console == null || console.isEmpty()) {
            console = "nes";
        }

        setupWebView(gameFile, console);
    }
    
    /**
     * S'assure que le serveur web est démarré pour les jeux WASM
     */
    private void ensureWebServerRunning() {
        if (isServiceRunning(WebServerService.class)) {
            Log.i(TAG, "WebServer already running");
            return;
        }
        
        try {
            Log.i(TAG, "Starting WebServer for WASM game...");
            Intent serviceIntent = new Intent(this, WebServerService.class);
            startForegroundService(serviceIntent);
            serverStartedByThisActivity = true;
            Log.i(TAG, "WebServer started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting WebServer: ", e);
        }
    }
    
    /**
     * Vérifie si un service est en cours d'exécution
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void setupFullscreenMode() {
        // Masquer la barre d'état et la navigation
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        // Garder l'écran allumé
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Masquer la barre d'action
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    private void setupWebView(String gameFile, String console) {
        webView = findViewById(R.id.webView);
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // Permissions réseau pour localhost
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setBlockNetworkImage(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                System.out.println("WebView loading URL: " + url);
                return false; // Laisser la WebView gérer toutes les URLs
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                System.out.println("WebView error: " + errorCode + " - " + description + " - " + failingUrl);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });

        // Get advanced configuration parameters from intent and config
        // Options de contrôle tactile
        float touchScale = getIntent().getFloatExtra("touchScale", 1.0f);
        float touchAlpha = getIntent().getFloatExtra("touchAlpha", 0.8f);
        String coreOverride = getIntent().getStringExtra("core");
        
        // Get PSX D-Pad setting from config
        ConsoleConfigActivity.ConsoleConfig config = ConsoleConfigActivity.getConfig(this, console);
        
        // Build URL with advanced parameters only
        String url = "";
        try {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("http://localhost:").append(WEBSERVER_PORT).append("/gamelibrary/emulator.html");
            urlBuilder.append("?game=").append(java.net.URLEncoder.encode(gameFile, "UTF-8"));
            urlBuilder.append("&console=").append(console);  // IMPORTANT: Pass console parameter!
            // Note: threads est géré par les loaders (loader_psp.js définit EJS_threads = true)
            urlBuilder.append("&touchScale=").append(touchScale);
            urlBuilder.append("&touchAlpha=").append(touchAlpha);
            
            // Add core override if specified
            if (coreOverride != null) {
                urlBuilder.append("&core=").append(coreOverride);
                System.out.println("EmulatorJS core override: " + coreOverride);
            }
            
            // Add D-Pad parameter for PSX if enabled
            if (config.useDpad && (console.equals("psx") || console.equals("ps1") || console.equals("playstation"))) {
                urlBuilder.append("&dpad=true");
                System.out.println("PSX D-Pad mode enabled");
            }
            
            url = urlBuilder.toString();
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error encoding URL: " + e.getMessage());
            url = "http://localhost:" + WEBSERVER_PORT + "/gamelibrary/emulator.html?game=" + gameFile + "&console=" + console;
        }
        
        System.out.println("Loading game with advanced config:");
        System.out.println("Game: " + gameFile);
        System.out.println("Touch Scale: " + touchScale + ", Touch Alpha: " + touchAlpha);
        System.out.println("(Threads/Core/Video/Audio managed by loaders and EmulatorJS settings)");
        
        // Load URL
        webView.loadUrl(url);
    }


    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Maintenir le mode plein écran même après perte de focus
            setupFullscreenMode();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Arrêter le serveur seulement si on l'a démarré et que l'option réseau n'est pas activée
        if (serverStartedByThisActivity) {
            WebServerPreferences prefs = WebServerPreferences.getInstance(this);
            if (!prefs.shouldKeepServerRunning()) {
                Log.i(TAG, "Stopping WebServer (network mode disabled)");
                Intent serviceIntent = new Intent(this, WebServerService.class);
                stopService(serviceIntent);
            } else {
                Log.i(TAG, "Keeping WebServer running (network mode enabled)");
            }
        }
    }
}
