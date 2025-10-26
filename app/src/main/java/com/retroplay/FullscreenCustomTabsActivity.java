package com.retroplay;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class FullscreenCustomTabsActivity extends Activity {
    private static final String TAG = "FullscreenCustomTabs";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Récupérer l'URL depuis l'Intent
        String url = getIntent().getStringExtra("url");
        if (url == null) {
            Log.e(TAG, "No URL provided");
            finish();
            return;
        }
        
        Log.i(TAG, "Launching Custom Tabs: " + url);
        
        // Configurer Custom Tabs avec interface minimale
        androidx.browser.customtabs.CustomTabsIntent.Builder builder = 
            new androidx.browser.customtabs.CustomTabsIntent.Builder();
        
        // Interface ultra-minimale
        builder.setShowTitle(false);
        builder.setUrlBarHidingEnabled(true);
        builder.setToolbarColor(android.graphics.Color.BLACK);
        builder.setSecondaryToolbarColor(android.graphics.Color.BLACK);
        
        androidx.browser.customtabs.CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(this, android.net.Uri.parse(url));
        
        // Fermer cette Activity car Custom Tabs prend le relais
        finish();
    }
}

