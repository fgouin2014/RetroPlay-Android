package com.retroplay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import android.Manifest;
import android.widget.Toast;
import android.util.Log;
import android.widget.Button;
import android.view.View;
import android.widget.FrameLayout;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import com.retroplay.fragments.KittFragment;

public class MainActivity extends FragmentActivity implements com.retroplay.fragments.KittFragment.KittFragmentListener {
    private WebView webView;
    private static final String TAG = "MainActivity";
    
    // Serveur web local (SEULEMENT WebServer pour EmulatorJS)
    private WebServer webServer;
    
    // Interface KITT
    private FrameLayout kittFragmentContainer;
    private FrameLayout kittDrawerContainer;
    private KittFragment kittFragment;
    private boolean isKittVisible = false;
    private boolean isKittPersistent = false;
    
    // Bouton pour accéder aux jeux
    private Button fabGames;
    
    // Permissions
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MainActivity onCreate started");
        setContentView(R.layout.activity_main);
        Log.i(TAG, "Layout loaded: activity_main");

        // Check and request permissions
        if (!checkPermissions()) {
            Log.i(TAG, "Missing permissions, requesting...");
            requestPermissions();
            return;
        }

        // Start WebServer only
        startWebServer();
        
        setupWebView();
        setupKittInterface();
        setupKittButton();
        setupGamesButton();
        
        Log.i(TAG, "MainActivity onCreate finished");
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded");
            }
        });

        webView.loadUrl("file:///android_asset/webapp/index.html");
    }

    private void setupKittInterface() {
        kittFragmentContainer = findViewById(R.id.kitt_fragment_container);
        kittDrawerContainer = findViewById(R.id.kitt_drawer_container);
        
        // Create KITT fragment
        kittFragment = new KittFragment();
        kittFragment.setKittFragmentListener(this);
        
        Log.i(TAG, "KITT interface initialized");
    }
    
    private void setupKittButton() {
        // KITT button is now integrated in web interface
        // Functionality is handled by JavaScript
        Log.i(TAG, "KITT button integrated in web interface");
    }
    
    private void setupGamesButton() {
        Log.i(TAG, "setupGamesButton started");
        fabGames = findViewById(R.id.fab_games);
        Log.i(TAG, "findViewById(R.id.fab_games) = " + fabGames);
        
        if (fabGames != null) {
            Log.i(TAG, "Button found, setting listener");
            fabGames.setOnClickListener(v -> {
                Log.i(TAG, "Games button clicked - Opening game list");
                try {
                    Intent intent = new Intent(this, GameListActivity.class);
                    startActivity(intent);
                    Log.i(TAG, "GameListActivity launched successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error launching GameListActivity", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            Log.i(TAG, "Games button configured successfully");
        } else {
            Log.e(TAG, "ERROR: fab_games not found in layout");
            Log.e(TAG, "Check that activity_main.xml contains android:id=\"@+id/fab_games\"");
        }
    }

    /**
     * Check if all permissions are granted
     */
    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        
        Log.i(TAG, "All permissions granted");
        return true;
    }
    
    /**
     * Request missing permissions
     */
    private void requestPermissions() {
        Log.i(TAG, "Requesting missing permissions");
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        
        // Request full storage access for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.i(TAG, "Requesting full storage access");
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
    
    /**
     * Handle permission request response
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                    allGranted = false;
                } else {
                    Log.i(TAG, "Permission granted: " + permissions[i]);
                }
            }
            
            if (allGranted) {
                Log.i(TAG, "All permissions granted, initializing app");
                // Restart app with permissions
                recreate();
            } else {
                Log.w(TAG, "Some permissions denied, limited features");
                Toast.makeText(this, "Some features may be limited without permissions", Toast.LENGTH_LONG).show();
                // Continue anyway with basic features
                Log.i(TAG, "Permissions denied, basic initialization");
                startWebServer();
                setupWebView();
                setupKittInterface();
                setupKittButton();
                setupGamesButton();
            }
        }
    }
    
    /**
     * Start WebServer (port 7777) for EmulatorJS
     */
    private void startWebServer() {
        try {
            Log.i(TAG, "Starting WebServer for EmulatorJS...");
            
            webServer = new WebServer(this);
            webServer.start();
            Log.i(TAG, "WebServer started on port 7777");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting WebServer: ", e);
            Toast.makeText(this, "Error starting WebServer", Toast.LENGTH_SHORT).show();
        }
    }

    public void openKittInterface() {
        try {
            if (!isKittVisible) {
                showKittInterface();
            } else {
                hideKittInterface();
            }
            Log.i(TAG, "KITT interface " + (isKittVisible ? "shown" : "hidden"));
        } catch (Exception e) {
            Log.e(TAG, "Error launching KITT: ", e);
            Toast.makeText(this, "Error launching KITT", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showKittInterface() {
        if (kittFragmentContainer != null && kittFragment != null) {
            // Hide WebView
            webView.setVisibility(View.GONE);
            
            // Show KITT container
            kittFragmentContainer.setVisibility(View.VISIBLE);
            
            // Add KITT fragment if not already added
            if (kittFragment.getParentFragmentManager() == null || 
                getSupportFragmentManager().findFragmentByTag("kitt_fragment") == null) {
                
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.kitt_fragment_container, kittFragment, "kitt_fragment");
                transaction.commit();
            }
            
            isKittVisible = true;
            Log.i(TAG, "KITT interface shown");
        }
    }
    
    public void hideKittInterface() {
        if (kittFragmentContainer != null) {
            // Hide KITT container
            kittFragmentContainer.setVisibility(View.GONE);
            
            // Show WebView
            webView.setVisibility(View.VISIBLE);
            
            isKittVisible = false;
            Log.i(TAG, "KITT interface hidden");
        }
    }

    @Override
    public void onBackPressed() {
        if (isKittVisible && !isKittPersistent) {
            // If KITT is visible and not in persistent mode, hide it
            hideKittInterface();
        } else if (isKittVisible && isKittPersistent) {
            // In persistent mode, don't allow closing KITT with back button
            Toast.makeText(this, "KITT persistent mode active - Use PERSIST button to disable", Toast.LENGTH_SHORT).show();
        } else if (webView.canGoBack()) {
            // If we can go back in WebView
            webView.goBack();
        } else {
            // Otherwise, close app
            super.onBackPressed();
        }
    }
    
    public void setKittPersistentMode(boolean persistent) {
        isKittPersistent = persistent;
        Log.i(TAG, "KITT persistent mode: " + (persistent ? "enabled" : "disabled"));
    }
    
    public void toggleKittPersistentMode() {
        setKittPersistentMode(!isKittPersistent);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop WebServer
        if (webServer != null) {
            webServer.stop();
            Log.i(TAG, "WebServer stopped");
        }
        
        Log.i(TAG, "MainActivity destroyed");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "App paused");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "App resumed");
    }
    
    /**
     * Get WebView for JavaScript execution
     */
    public WebView getWebView() {
        return webView;
    }
}
