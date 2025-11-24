package com.retroplay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import android.Manifest;
import android.net.Uri;

/**
 * SplashActivity - Écran de démarrage qui gère:
 * - Demande des permissions
 * - Scan automatique des gamelist.json (en parallèle)
 * - Lance MainActivity une fois tout prêt
 */
public class SplashActivity extends FragmentActivity {
    private static final String TAG = "SplashActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final long SCAN_TIMEOUT_MS = 60000; // 60 secondes max pour le scan (augmenté pour gros scans)
    
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView detailsText;
    private boolean permissionsGranted = false;
    private boolean scanCompleted = false;
    private boolean preloadCompleted = false;
    private boolean scanStarted = false;
    private boolean preloadStarted = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private int scannedConsolesCount = 0;
    private int totalGamesCount = 0;
    private int preloadedCount = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "SplashActivity onCreate started");
        
        // Layout simple avec progress bar
        setContentView(R.layout.activity_splash);
        
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        detailsText = findViewById(R.id.detailsText);
        
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(true);
        }
        
        if (detailsText != null) {
            detailsText.setVisibility(View.VISIBLE);
        }
        
        updateStatus("Initializing...");
        updateDetails("");
        
        // Vérifier les permissions
        checkAndRequestPermissions();
    }
    
    /**
     * Vérifie et demande les permissions
     */
    private void checkAndRequestPermissions() {
        if (checkPermissions()) {
            Log.i(TAG, "All permissions already granted");
            permissionsGranted = true;
            startScanAndWait();
        } else {
            Log.i(TAG, "Missing permissions, requesting...");
            updateStatus("Requesting permissions...");
            requestPermissions();
        }
    }
    
    /**
     * Vérifie si toutes les permissions sont accordées
     */
    private boolean checkPermissions() {
        // Sur Android 11+, vérifier MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted");
                return false;
            }
            // Vérifier les autres permissions (sauf READ/WRITE_EXTERNAL_STORAGE obsolètes)
            for (String permission : REQUIRED_PERMISSIONS) {
                if (!permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE) && 
                    !permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "Missing permission: " + permission);
                        return false;
                    }
                }
            }
        } else {
            // Sur Android 10 et inférieur, vérifier toutes les permissions normalement
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing permission: " + permission);
                    return false;
                }
            }
        }
        
        Log.i(TAG, "All permissions granted");
        return true;
    }
    
    /**
     * Demande les permissions manquantes
     */
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        
        // Demander l'accès complet au stockage pour Android 11+
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
     * Démarre le scan et attend qu'il soit terminé (ou timeout)
     */
    private void startScanAndWait() {
        if (scanStarted) {
            return; // Déjà lancé
        }
        
        scanStarted = true;
        updateStatus("Preparing scan...");
        updateDetails("Checking console directories...");
        
        // Démarrer le WebServerService en parallèle pour éviter de l'attendre dans GameListActivity
        startWebServerServiceEarly();
        
        // Précharger les gamelist.json existants en parallèle
        startPreloadGamelists();
        
        // Timeout pour éviter d'attendre indéfiniment
        timeoutRunnable = () -> {
            Log.w(TAG, "Scan/preload timeout reached, proceeding anyway");
            scanCompleted = true;
            preloadCompleted = true;
            checkAndLaunchMainActivity();
        };
        handler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS);
        
        // Lancer le scan en arrière-plan
        new Thread(() -> {
            try {
                // Vérifier si c'est le premier démarrage
                boolean isFirstLaunch = com.retroplay.GamelistScanner.INSTANCE.isFirstLaunch(this);
                
                if (isFirstLaunch) {
                    Log.i(TAG, "First launch detected - Starting full console scan");
                    updateStatus("Initial scan starting...");
                    updateDetails("This may take a few moments...");
                    
                    // Petit délai pour que l'utilisateur voie le message
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Scan complet
                    com.retroplay.GamelistScanner.scanAllConsolesAsync(this, new com.retroplay.ScanCallback() {
                        @Override
                        public void onProgress(String consoleName, int current, int total, int gamesFound) {
                            handler.post(() -> {
                                if (consoleName == null) {
                                    // Début du scan
                                    updateStatus("Starting scan...");
                                    updateDetails("Scanning " + total + " console" + (total != 1 ? "s" : "") + "...");
                                } else {
                                    String consoleDisplayName = consoleName.toUpperCase();
                                    if (gamesFound > 0) {
                                        updateStatus("Scanning " + consoleDisplayName + "...");
                                        updateDetails("Found " + gamesFound + " game" + (gamesFound != 1 ? "s" : "") + 
                                            " (" + (current + 1) + "/" + total + " consoles)");
                                    } else if (gamesFound == 0) {
                                        updateStatus("Scanning " + consoleDisplayName + "...");
                                        updateDetails("No games found (" + (current + 1) + "/" + total + " consoles)");
                                    } else if (gamesFound == -1) {
                                        updateStatus("Checking " + consoleDisplayName + "...");
                                        updateDetails("Gamelist already exists (" + (current + 1) + "/" + total + " consoles)");
                                    } else if (gamesFound == -3) {
                                        updateStatus("Scanning " + consoleDisplayName + "...");
                                        updateDetails("Analyzing ROM files... (" + (current + 1) + "/" + total + " consoles)");
                                    } else if (gamesFound == -2) {
                                        updateStatus("Error scanning " + consoleDisplayName);
                                        updateDetails("(" + (current + 1) + "/" + total + " consoles)");
                                    } else {
                                        updateStatus("Scanning " + consoleDisplayName + "...");
                                        updateDetails("(" + (current + 1) + "/" + total + " consoles)");
                                    }
                                }
                            });
                        }
                        
                        @Override
                        public void onComplete(com.retroplay.GamelistScanner.ScanResult result) {
                            handler.removeCallbacks(timeoutRunnable);
                            scanCompleted = true;
                            
                            scannedConsolesCount = result.getScannedConsoles().size();
                            totalGamesCount = result.getTotalGames();
                            
                            if (result.getScannedConsoles().size() > 0) {
                                Log.i(TAG, "Initial scan completed: " + result.getScannedConsoles().size() + 
                                    " consoles, " + result.getTotalGames() + " games");
                                updateStatus("Scan completed!");
                                updateDetails("Found " + result.getScannedConsoles().size() + 
                                    " console" + (result.getScannedConsoles().size() > 1 ? "s" : "") + 
                                    " with " + result.getTotalGames() + " game" + (result.getTotalGames() != 1 ? "s" : ""));
                            } else {
                                Log.i(TAG, "Initial scan completed: No consoles found or all already have gamelist.json");
                                updateStatus("Scan completed");
                                updateDetails("No ROM directories found.\nAdd ROMs to /GameLibrary-Data/");
                            }
                            
                            if (result.getErrors().size() > 0) {
                                Log.w(TAG, "Scan errors: " + result.getErrors().size());
                                String errorMsg = result.getErrors().size() + " error" + 
                                    (result.getErrors().size() > 1 ? "s" : "") + " occurred";
                                if (detailsText != null) {
                                    handler.post(() -> {
                                        String currentDetails = detailsText.getText().toString();
                                        if (!currentDetails.isEmpty()) {
                                            detailsText.setText(currentDetails + "\n" + errorMsg);
                                        } else {
                                            detailsText.setText(errorMsg);
                                        }
                                    });
                                }
                            }
                            
                            // Vérifier si on peut lancer (attendre aussi le préchargement)
                            checkAndLaunchMainActivity();
                        }
                        
                        @Override
                        public void onError(String error) {
                            handler.removeCallbacks(timeoutRunnable);
                            scanCompleted = true;
                            Log.e(TAG, "Error in initial scan: " + error);
                            updateStatus("Scan error");
                            updateDetails("Could not complete scan.\n" + error);
                            // Vérifier si on peut lancer (attendre aussi le préchargement)
                            checkAndLaunchMainActivity();
                        }
                    });
                } else {
                    // Démarrages suivants: vérifier si auto-scan est activé
                    boolean autoScanEnabled = com.retroplay.GamelistScanner.INSTANCE.isAutoScanEnabled(this);
                    if (autoScanEnabled) {
                        Log.i(TAG, "Auto-scan enabled - Scanning for new ROMs");
                        updateStatus("Checking for updates...");
                        updateDetails("Scanning for new ROMs...");
                        
                        // Petit délai pour que l'utilisateur voie le message
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Scan incrémental
                        com.retroplay.GamelistScanner.scanNewRomsAsync(this, new com.retroplay.ScanCallback() {
                            @Override
                            public void onProgress(String consoleName, int current, int total, int gamesFound) {
                                handler.post(() -> {
                                    if (consoleName == null) {
                                        // Début du scan
                                        updateStatus("Checking for new ROMs...");
                                        updateDetails("Scanning " + total + " console" + (total != 1 ? "s" : "") + "...");
                                    } else {
                                        String consoleDisplayName = consoleName.toUpperCase();
                                        if (gamesFound > 0) {
                                            updateStatus("Updating " + consoleDisplayName + "...");
                                            updateDetails("Found " + gamesFound + " new game" + (gamesFound != 1 ? "s" : "") + 
                                                " (" + (current + 1) + "/" + total + " consoles)");
                                        } else if (gamesFound == 0) {
                                            updateStatus("Checking " + consoleDisplayName + "...");
                                            updateDetails("No new games (" + (current + 1) + "/" + total + " consoles)");
                                        } else if (gamesFound == -1) {
                                            updateStatus("Checking " + consoleDisplayName + "...");
                                            updateDetails("No changes needed (" + (current + 1) + "/" + total + " consoles)");
                                        } else if (gamesFound == -3) {
                                            updateStatus("Scanning " + consoleDisplayName + "...");
                                            updateDetails("Analyzing ROM files... (" + (current + 1) + "/" + total + " consoles)");
                                        } else if (gamesFound == -2) {
                                            updateStatus("Error checking " + consoleDisplayName);
                                            updateDetails("(" + (current + 1) + "/" + total + " consoles)");
                                        } else {
                                            updateStatus("Checking " + consoleDisplayName + "...");
                                            updateDetails("(" + (current + 1) + "/" + total + " consoles)");
                                        }
                                    }
                                });
                            }
                            
                            @Override
                            public void onComplete(com.retroplay.GamelistScanner.ScanResult result) {
                                handler.removeCallbacks(timeoutRunnable);
                                scanCompleted = true;
                                
                                if (result.getScannedConsoles().size() > 0) {
                                    Log.i(TAG, "Rescan completed: " + result.getScannedConsoles().size() + 
                                        " consoles updated");
                                    updateStatus("Update completed");
                                    updateDetails("Updated " + result.getScannedConsoles().size() + 
                                        " console" + (result.getScannedConsoles().size() > 1 ? "s" : "") + 
                                        " with " + result.getTotalGames() + " new game" + 
                                        (result.getTotalGames() != 1 ? "s" : ""));
                                } else {
                                    Log.d(TAG, "Rescan completed: No new ROMs found");
                                    updateStatus("Ready");
                                    updateDetails("No new ROMs found");
                                }
                                
                                // Vérifier si on peut lancer (attendre aussi le préchargement)
                                checkAndLaunchMainActivity();
                            }
                            
                            @Override
                            public void onError(String error) {
                                handler.removeCallbacks(timeoutRunnable);
                                scanCompleted = true;
                                Log.e(TAG, "Error in rescan: " + error);
                                updateStatus("Ready");
                                updateDetails("Scan error: " + error);
                                // Vérifier si on peut lancer (attendre aussi le préchargement)
                                checkAndLaunchMainActivity();
                            }
                        });
                    } else {
                        // Auto-scan désactivé, pas de scan
                        handler.removeCallbacks(timeoutRunnable);
                        scanCompleted = true;
                        Log.i(TAG, "Auto-scan disabled - Skipping scan");
                        updateStatus("Ready");
                        updateDetails("Auto-scan disabled");
                        // Vérifier si on peut lancer (attendre aussi le préchargement)
                        checkAndLaunchMainActivity();
                    }
                }
            } catch (Exception e) {
                handler.removeCallbacks(timeoutRunnable);
                scanCompleted = true;
                Log.e(TAG, "Error starting scan: " + e.getMessage(), e);
                updateStatus("Ready");
                checkAndLaunchMainActivity();
            }
        }).start();
    }
    
    /**
     * Démarre le préchargement des gamelist.json existants
     */
    private void startPreloadGamelists() {
        if (preloadStarted) {
            return; // Déjà lancé
        }
        
        preloadStarted = true;
        Log.i(TAG, "Starting gamelist preload...");
        
        // Précharger en arrière-plan
        new Thread(() -> {
            com.retroplay.GamelistCache.INSTANCE.preloadAllGamelists(this, new com.retroplay.GamelistCache.PreloadCallback() {
                @Override
                public void onProgress(String consoleName, int current, int total) {
                    handler.post(() -> {
                        if (current == 1 && total > 1) {
                            updateDetails("Preloading gamelist.json... (" + current + "/" + total + ")");
                        } else if (current > 1) {
                            updateDetails("Preloading gamelist.json... (" + current + "/" + total + ")");
                        }
                    });
                }
                
                @Override
                public void onComplete(int loadedCount) {
                    handler.post(() -> {
                        preloadCompleted = true;
                        preloadedCount = loadedCount;
                        Log.i(TAG, "Gamelist preload complete: " + loadedCount + " gamelist.json loaded");
                        checkAndLaunchMainActivity();
                    });
                }
            });
        }).start();
    }
    
    /**
     * Vérifie si on peut lancer MainActivity (permissions + scan terminé + préchargement terminé)
     */
    private void checkAndLaunchMainActivity() {
        if (permissionsGranted && scanCompleted && preloadCompleted) {
            Log.i(TAG, "All ready (scan: " + scannedConsolesCount + " consoles, preload: " + preloadedCount + " gamelist.json), launching MainActivity");
            updateStatus("Ready!");
            updateDetails(scannedConsolesCount > 0 ? 
                scannedConsolesCount + " console" + (scannedConsolesCount > 1 ? "s" : "") + 
                " ready with " + totalGamesCount + " game" + (totalGamesCount != 1 ? "s" : "") :
                "Ready");
            
            // Lancer immédiatement (délais supprimés pour optimisation)
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }
    
    /**
     * Met à jour le texte de statut principal
     */
    private void updateStatus(String status) {
        handler.post(() -> {
            if (statusText != null) {
                statusText.setText(status);
            }
            Log.d(TAG, "Status: " + status);
        });
    }
    
    /**
     * Met à jour le texte de détails
     */
    private void updateDetails(String details) {
        handler.post(() -> {
            if (detailsText != null) {
                detailsText.setText(details);
            }
            if (!details.isEmpty()) {
                Log.d(TAG, "Details: " + details);
            }
        });
    }
    
    /**
     * Démarre le WebServerService tôt pour éviter de l'attendre dans GameListActivity
     */
    private void startWebServerServiceEarly() {
        new Thread(() -> {
            try {
                // Vérifier si le service est déjà démarré
                if (isServiceRunning(com.retroplay.WebServerService.class)) {
                    Log.i(TAG, "WebServerService already running");
                    return;
                }
                
                // Démarrer le service
                Log.i(TAG, "Starting WebServerService early in SplashActivity...");
                Intent serviceIntent = new Intent(this, com.retroplay.WebServerService.class);
                startForegroundService(serviceIntent);
                
                // Attendre un peu que le service démarre
                Thread.sleep(200);
                
                // Vérifier que le WebServer est prêt (max 3 secondes)
                int maxRetries = 6;
                int retryDelay = 500;
                boolean serverReady = false;
                
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        java.net.URL testUrl = new java.net.URL("http://localhost:7777/gamelibrary/api/consoles");
                        java.net.HttpURLConnection testConn = (java.net.HttpURLConnection) testUrl.openConnection();
                        testConn.setRequestMethod("GET");
                        testConn.setConnectTimeout(500);
                        testConn.setReadTimeout(500);
                        int responseCode = testConn.getResponseCode();
                        testConn.disconnect();
                        
                        if (responseCode == 200) {
                            serverReady = true;
                            Log.i(TAG, "WebServer ready in SplashActivity after " + (i + 1) + " attempts");
                            break;
                        }
                    } catch (Exception e) {
                        if (i < maxRetries - 1) {
                            Thread.sleep(retryDelay);
                        }
                    }
                }
                
                if (!serverReady) {
                    Log.w(TAG, "WebServer not ready in SplashActivity, will retry in GameListActivity");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting WebServerService early: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Vérifie si un service est déjà en cours d'exécution
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
    
    /**
     * Gère la réponse aux demandes de permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                // Sur Android 11+, ignorer READ/WRITE_EXTERNAL_STORAGE (obsolètes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                    (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE) || 
                     permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                    Log.i(TAG, "Ignoring obsolete permission on Android 11+: " + permissions[i]);
                    continue;
                }
                
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                    allGranted = false;
                } else {
                    Log.i(TAG, "Permission granted: " + permissions[i]);
                }
            }
            
            // Vérifier MANAGE_EXTERNAL_STORAGE sur Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    allGranted = false;
                    Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted");
                }
            }
            
            if (allGranted) {
                Log.i(TAG, "All permissions granted");
                permissionsGranted = true;
                updateStatus("Permissions granted");
                updateDetails("Starting scan...");
                startScanAndWait();
            } else {
                Log.w(TAG, "Some permissions denied");
                updateStatus("Permissions required");
                updateDetails("Some permissions are missing.\nContinuing anyway...");
                // Attendre un peu puis lancer quand même (l'utilisateur peut les accorder plus tard)
                handler.postDelayed(() -> {
                    permissionsGranted = true; // On continue quand même
                    startScanAndWait();
                }, 2000);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Vérifier à nouveau les permissions au retour des paramètres
        if (!permissionsGranted && checkPermissions()) {
            permissionsGranted = true;
            updateStatus("Permissions granted");
            startScanAndWait();
        }
    }
}

