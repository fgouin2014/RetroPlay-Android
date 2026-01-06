package com.retroplay.server;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Handler pour le parsing des requêtes HTTP.
 * Extracted from WebServer to improve modularity and testability.
 * 
 * Gère:
 * - Parsing de la ligne de requête HTTP
 * - Extraction de la méthode (GET, HEAD, etc.)
 * - Extraction du chemin
 * - Lecture des headers HTTP
 * - Détection du Host header pour SharedArrayBuffer
 */
public class HttpRequestHandler {
    
    private static final String TAG = "HttpRequestHandler";
    
    /**
     * Résultat du parsing d'une requête HTTP
     */
    public static class HttpRequest {
        public final String method;
        public final String path;
        public final boolean enableSharedArrayBuffer;
        public final String hostHeader;
        
        public HttpRequest(String method, String path, boolean enableSharedArrayBuffer, String hostHeader) {
            this.method = method;
            this.path = path;
            this.enableSharedArrayBuffer = enableSharedArrayBuffer;
            this.hostHeader = hostHeader;
        }
    }
    
    /**
     * Parse une requête HTTP depuis un socket client
     * 
     * @param clientSocket Socket du client
     * @return HttpRequest parsée, ou null si la requête est invalide
     */
    public HttpRequest parseRequest(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()))) {
            
            // Lire la ligne de requête HTTP
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                Log.w(TAG, "Empty request line");
                return null;
            }
            
            Log.d(TAG, "Requête: " + requestLine);
            
            // Extraire la méthode et le chemin
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                Log.w(TAG, "Invalid request line: " + requestLine);
                return null;
            }
            
            String method = parts[0];
            String path = parts[1];
            
            // Valider la méthode HTTP
            if (!isValidMethod(method)) {
                Log.w(TAG, "Unsupported method: " + method);
                return null;
            }
            
            // Lire les headers HTTP et extraire le Host
            String hostHeader = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("host:")) {
                    hostHeader = line.substring(5).trim();
                }
            }
            
            // Déterminer si on doit envoyer les headers COEP/COOP
            // Seulement pour localhost (Chrome les refuse sur les IPs non-HTTPS)
            boolean enableSharedArrayBuffer = (hostHeader != null && 
                (hostHeader.startsWith("localhost") || hostHeader.startsWith("127.0.0.1")));
            
            return new HttpRequest(method, path, enableSharedArrayBuffer, hostHeader);
            
        } catch (IOException e) {
            Log.e(TAG, "Erreur parsing requête HTTP", e);
            return null;
        }
    }
    
    /**
     * Vérifie si une méthode HTTP est supportée
     */
    private boolean isValidMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }
    
    /**
     * Nettoie et décode un chemin d'URL
     */
    public String cleanPath(String path) {
        if (path == null) {
            return "/";
        }
        
        // Enlever les paramètres de requête
        String cleanPath = path;
        if (cleanPath.contains("?")) {
            cleanPath = cleanPath.substring(0, cleanPath.indexOf("?"));
        }
        
        // Décoder l'URL
        try {
            cleanPath = java.net.URLDecoder.decode(cleanPath, "UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode URL: " + cleanPath);
        }
        
        return cleanPath;
    }
}

