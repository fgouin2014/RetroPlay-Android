package com.retroplay.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.retroplay.R
import android.util.Log

/**
 * Activity pour afficher une WebView indépendante
 * Charge le fichier gamelibrary/index.html depuis les assets
 * Supporte le mode plein écran immersif
 */
class GameLibraryWebViewActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_library_webview)
        
        // Configuration du mode plein écran immersif
        setupImmersiveMode()
        
        // Initialisation de la WebView
        setupWebView()
        
        // Chargement du fichier HTML local
        loadLocalHtml()
    }
    
    /**
     * Configuration du mode plein écran immersif
     */
    private fun setupImmersiveMode() {
        // Masquer la barre de navigation et la barre de statut
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        
        // Garder l'écran allumé
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    /**
     * Configuration de la WebView avec tous les paramètres requis
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        
        // Configuration des paramètres de la WebView
        val webSettings: WebSettings = webView.settings
        
        // Activer JavaScript
        webSettings.javaScriptEnabled = true
        
        // Activer DOM Storage
        webSettings.domStorageEnabled = true
        
        // Support du responsive
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        
        // Autres paramètres utiles
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        @Suppress("DEPRECATION")
        webSettings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.databaseEnabled = true
        
        // Autoriser les ressources externes (CDN, etc.)
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.blockNetworkLoads = false
        webSettings.blockNetworkImage = false
        
        // Configuration des clients
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("GameLibraryWebView", "Page chargée: $url")
            }
            
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                @Suppress("DEPRECATION")
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("GameLibraryWebView", "Erreur WebView: $description")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                Log.d("GameLibraryWebView", "Progression: $newProgress%")
            }
        }
    }
    
    /**
     * Chargement du fichier HTML depuis le WebServer
     * Charge depuis /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/ au lieu des assets
     */
    private fun loadLocalHtml() {
        try {
            val url = "http://localhost:7777/gamelibrary/index.html"
            webView.loadUrl(url)
            Log.d("GameLibraryWebView", "Chargement de: $url")
        } catch (e: Exception) {
            Log.e("GameLibraryWebView", "Erreur lors du chargement du fichier HTML", e)
        }
    }
    
    /**
     * Gestion du bouton retour
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    /**
     * Nettoyage des ressources
     */
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
    
    /**
     * Gestion de la pause/reprise pour optimiser les performances
     */
    override fun onPause() {
        super.onPause()
        webView.onPause()
    }
    
    override fun onResume() {
        super.onResume()
        webView.onResume()
    }
    
    /**
     * Gestion du changement de visibilité pour le mode immersif
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }
}
