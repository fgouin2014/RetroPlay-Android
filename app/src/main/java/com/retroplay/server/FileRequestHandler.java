package com.retroplay.server;

import android.util.Log;
import com.retroplay.server.HtmlGenerator;
import com.retroplay.utils.WebServerUtils;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handler pour la gestion des fichiers statiques.
 * Extracted from WebServer to improve modularity and testability.
 * 
 * Gère:
 * - Service des fichiers depuis SITES_DIR
 * - Listing des répertoires
 * - Gestion des types MIME
 * - Headers HTTP (CORS, cache, etc.)
 */
public class FileRequestHandler {
    
    private static final String TAG = "FileRequestHandler";
    private final String sitesDir;
    private final boolean foldersFirst;
    private final boolean exactSize;
    private final String customCSS;
    
    public FileRequestHandler(String sitesDir, boolean foldersFirst, boolean exactSize, String customCSS) {
        this.sitesDir = sitesDir;
        this.foldersFirst = foldersFirst;
        this.exactSize = exactSize;
        this.customCSS = customCSS;
    }
    
    /**
     * Sert un fichier statique ou un listing de répertoire
     * 
     * @param outputStream Stream de sortie HTTP
     * @param cleanPath Chemin nettoyé de la requête
     * @param method Méthode HTTP (GET, HEAD)
     */
    public void serveFile(OutputStream outputStream, String cleanPath, String method) throws IOException {
        try {
            // Construire le chemin complet
            Path filePath = Paths.get(sitesDir, cleanPath.substring(1)); // Enlever le premier /
            
            Log.d(TAG, "Serving file: " + filePath);
            
            if (!Files.exists(filePath)) {
                sendErrorResponse(outputStream, 404, "Not Found");
                return;
            }
            
            if (Files.isDirectory(filePath)) {
                // Lister le répertoire
                serveDirectory(outputStream, filePath, cleanPath);
                return;
            }
            
            // Servir le fichier
            serveStaticFile(outputStream, filePath);
            
        } catch (java.net.SocketException e) {
            // "Broken pipe" est normal quand le client ferme la connexion
            if (e.getMessage() == null || !e.getMessage().contains("Broken pipe")) {
                Log.e(TAG, "Erreur socket service fichier: " + cleanPath, e);
            } else {
                Log.d(TAG, "Client closed connection during file serve: " + cleanPath);
            }
        } catch (java.io.IOException e) {
            // Autres IOExceptions
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection during file serve: " + cleanPath);
            } else {
                Log.e(TAG, "Erreur IO service fichier: " + cleanPath, e);
                try {
                    sendErrorResponse(outputStream, 500, "Internal Server Error");
                } catch (Exception ignored) {
                    // Ignorer si le client a déjà fermé la connexion
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur service fichier: " + cleanPath, e);
            try {
                sendErrorResponse(outputStream, 500, "Internal Server Error");
            } catch (Exception ignored) {
                // Ignorer si le client a déjà fermé la connexion
            }
        }
    }
    
    /**
     * Sert un fichier statique avec gestion directe des bytes
     */
    private void serveStaticFile(OutputStream outputStream, Path filePath) throws IOException {
        // Lire le fichier en bytes
        byte[] fileBytes = Files.readAllBytes(filePath);
        
        // Déterminer le type MIME
        String mimeType = WebServerUtils.getMimeType(filePath.getFileName().toString());
        
        // Headers HTTP
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 200 OK\r\n");
        headers.append("Content-Type: ").append(mimeType).append("\r\n");
        headers.append("Content-Length: ").append(fileBytes.length).append("\r\n");
        headers.append("Server: ChatAI-WebServer/1.0 (Android)\r\n");
        headers.append("Access-Control-Allow-Origin: *\r\n");
        headers.append("Cache-Control: public, max-age=3600\r\n");
        headers.append("\r\n");
        
        // Envoyer les headers
        outputStream.write(headers.toString().getBytes("UTF-8"));
        
        // Envoyer le contenu du fichier (bytes bruts)
        outputStream.write(fileBytes);
        outputStream.flush();
        
        Log.d(TAG, "File served: " + filePath.getFileName() + " (" + fileBytes.length + " bytes)");
    }
    
    /**
     * Sert un répertoire (listing)
     */
    private void serveDirectory(OutputStream outputStream, Path dirPath, String urlPath) throws IOException {
        try {
            // Convertir les Path en FileInfo pour HtmlGenerator
            java.util.List<Path> paths = new java.util.ArrayList<>();
            Files.list(dirPath).forEach(paths::add);
            
            java.util.List<HtmlGenerator.FileInfo> fileInfos = new java.util.ArrayList<>();
            for (Path filePath : paths) {
                try {
                    fileInfos.add(HtmlGenerator.FileInfo.fromFile(filePath.toFile()));
                } catch (Exception e) {
                    Log.e(TAG, "Erreur conversion Path to FileInfo: " + filePath, e);
                }
            }
            
            // Générer le HTML via HtmlGenerator
            String htmlContent = HtmlGenerator.generateDirectoryListing(
                urlPath, 
                fileInfos, 
                foldersFirst, 
                exactSize, 
                customCSS
            );
            
            byte[] htmlBytes = htmlContent.getBytes("UTF-8");
            
            // Headers HTTP
            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 200 OK\r\n");
            headers.append("Content-Type: text/html; charset=utf-8\r\n");
            headers.append("Content-Length: ").append(htmlBytes.length).append("\r\n");
            headers.append("Server: ChatAI-WebServer/1.0 (Android)\r\n");
            headers.append("Access-Control-Allow-Origin: *\r\n");
            headers.append("\r\n");
            
            // Envoyer la réponse
            outputStream.write(headers.toString().getBytes("UTF-8"));
            outputStream.write(htmlBytes);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Erreur listing directory: " + dirPath, e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * Envoie une réponse d'erreur HTTP
     */
    private void sendErrorResponse(OutputStream outputStream, int statusCode, String message) throws IOException {
        try {
            String response = "HTTP/1.1 " + statusCode + " " + message + "\r\n" +
                           "Content-Type: text/plain\r\n" +
                           "Content-Length: " + message.length() + "\r\n" +
                           "\r\n" + message;
            outputStream.write(response.getBytes("UTF-8"));
            outputStream.flush();
        } catch (java.net.SocketException e) {
            // "Broken pipe" est normal quand le client ferme la connexion
            if (e.getMessage() == null || !e.getMessage().contains("Broken pipe")) {
                throw e; // Re-lancer si ce n'est pas un "Broken pipe"
            }
            // Sinon, ignorer silencieusement
        } catch (java.io.IOException e) {
            // Autres IOExceptions
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                // Ignorer silencieusement
                return;
            }
            throw e; // Re-lancer les autres IOExceptions
        }
    }
}

