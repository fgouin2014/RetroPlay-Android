package com.retroplay;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Service en arrière-plan pour maintenir le WebServer actif
 * RetroPlay - Port 7777 pour EmulatorJS uniquement
 */
public class WebServerService extends Service {
    private static final String TAG = "WebServerService";
    private static final String CHANNEL_ID = "webserver_channel";
    private static final int NOTIFICATION_ID = 7777;
    
    private WebServer webServer;
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        WebServerService getService() {
            return WebServerService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "WebServerService onCreate");
        
        createNotificationChannel();
        startWebServer();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "WebServerService onStartCommand");
        
        // ⭐ FIX Android 12+: Start as foreground service with proper service type
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+) - Use FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                // (dataSync type may not be appropriate for WebServer)
                startForeground(NOTIFICATION_ID, createNotification(), 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12-13 (API 31-33) - Use dataSync type (declared in manifest)
                startForeground(NOTIFICATION_ID, createNotification(), 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                // Android 11 and below - No service type required
                startForeground(NOTIFICATION_ID, createNotification());
            }
            Log.i(TAG, "WebServerService started as foreground service");
        } catch (ForegroundServiceStartNotAllowedException e) {
            // ⭐ FIX: Handle exception gracefully - try as regular service
            Log.e(TAG, "Foreground service not allowed, starting as regular service: " + e.getMessage());
            // Continue as regular service (may be killed by system, but better than crash)
            try {
                startForeground(NOTIFICATION_ID, createNotification());
            } catch (Exception e2) {
                Log.e(TAG, "Failed to start service: " + e2.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service: " + e.getMessage(), e);
        }
        
        return START_STICKY; // Restart service if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "WebServerService onDestroy");
        
        // Stop WebServer
        if (webServer != null) {
            webServer.stop();
            Log.i(TAG, "WebServer stopped");
        }
    }
    
    private void startWebServer() {
        try {
            Log.i(TAG, "Starting WebServer on port 7777...");
            webServer = new WebServer(this);
            webServer.start();
            Log.i(TAG, "WebServer started successfully on port 7777");
            
            // Update notification with IP address
            updateNotification();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting WebServer", e);
        }
    }
    
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "RetroPlay WebServer",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("EmulatorJS WebServer - Port 7777");
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        String deviceIP = getDeviceIP();
        String message = "WebServer running on port 7777\n" + 
                        "Access: http://" + deviceIP + ":7777/";
        
        Intent notificationIntent = new Intent(this, GameListActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RetroPlay WebServer")
            .setContentText(message)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }
    
    /**
     * Get device IP address for WiFi connection
     */
    private String getDeviceIP() {
        try {
            // Try WiFi first
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                @SuppressWarnings("deprecation")
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                
                if (ipAddress != 0) {
                    return String.format("%d.%d.%d.%d",
                        (ipAddress & 0xff),
                        (ipAddress >> 8 & 0xff),
                        (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff));
                }
            }
            
            // Fallback: check all network interfaces
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    for (java.net.InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                        if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                            String ip = address.getHostAddress();
                            if (ip != null && isValidIP(ip)) {
                                return ip;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device IP", e);
        }
        
        return "localhost";
    }
    
    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return !ip.startsWith("127.") && !ip.startsWith("169.254.");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Vérifie si le serveur web est en cours d'exécution et prêt
     */
    public boolean isServerRunning() {
        return webServer != null && webServer.isRunning();
    }
    
    /**
     * Retourne l'instance du WebServer (pour vérification externe)
     */
    public WebServer getWebServer() {
        return webServer;
    }
}

