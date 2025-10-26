package com.retroplay;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Serveur Web simple et efficace pour servir des fichiers statiques
 * Port: 7777
 * Gestion directe des bytes (pas de String)
 */
public class WebServer {
    private static final String TAG = "WebServer";
    private static final int PORT = 7777;
    private static final String SITES_DIR = "/storage/emulated/0/RetroPlay-Files/sites";
    
    // Options configurables (comme Apache/Nginx)
    private boolean autoindex = true;
    private boolean foldersFirst = true;
    private boolean exactSize = false;
    private boolean showIcons = true;
    private String customCSS = "";
    
    private Context context;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread serverThread;
    
    public WebServer(Context context) {
        this.context = context;
    }
    
    // Méthodes de configuration (comme Apache/Nginx)
    public void setAutoindex(boolean enabled) {
        this.autoindex = enabled;
    }
    
    public void setFoldersFirst(boolean enabled) {
        this.foldersFirst = enabled;
    }
    
    public void setExactSize(boolean enabled) {
        this.exactSize = enabled;
    }
    
    public void setShowIcons(boolean enabled) {
        this.showIcons = enabled;
    }
    
    public void setCustomCSS(String css) {
        this.customCSS = css;
    }
    
    /**
     * Démarre le serveur web
     */
    public void start() {
        // Fermer proprement le serveur existant s'il y en a un
        if (isRunning || serverSocket != null) {
            Log.w(TAG, "Serveur web déjà en cours, fermeture avant redémarrage");
            stop();
            try {
                Thread.sleep(500); // Attendre que le socket soit complètement libéré
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        try {
            serverSocket = new ServerSocket(PORT);
            isRunning = true;
            
            serverThread = new Thread(() -> {
                Log.i(TAG, "Serveur web démarré sur le port " + PORT);
                
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // Traiter chaque connexion dans un thread séparé
                        new Thread(() -> handleClient(clientSocket)).start();
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Erreur acceptation connexion", e);
                        }
                    }
                }
            });
            
            serverThread.start();
            Log.i(TAG, "Serveur web prêt sur http://localhost:" + PORT);
            
        } catch (IOException e) {
            Log.e(TAG, "Erreur démarrage serveur web", e);
            isRunning = false;
        }
    }
    
    /**
     * Arrête le serveur web
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Erreur arrêt serveur web", e);
        }
        
        if (serverThread != null) {
            serverThread.interrupt();
        }
        
        Log.i(TAG, "Serveur web arrêté");
    }
    
    /**
     * Vérifie si le serveur est en cours d'exécution
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Gère une connexion client
     */
    private void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream outputStream = clientSocket.getOutputStream()) {
            
            // Lire la requête HTTP
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            
            Log.d(TAG, "Requête: " + requestLine);
            
            // Extraire le chemin de la requête
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendErrorResponse(outputStream, 400, "Bad Request");
                return;
            }
            
            String method = parts[0];
            String path = parts[1];
            
            // Accepter GET et HEAD
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                sendErrorResponse(outputStream, 405, "Method Not Allowed");
                return;
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
            
            // Servir le fichier avec ou sans headers COEP/COOP selon l'hôte
            serveFile(outputStream, path, method, enableSharedArrayBuffer);
            
        } catch (IOException e) {
            Log.e(TAG, "Erreur traitement client", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Erreur fermeture socket", e);
            }
        }
    }
    
    /**
     * Sert un fichier (version simple sans Range Requests)
     */
    private void serveFile(OutputStream outputStream, String path, String method, boolean enableSharedArrayBuffer) throws IOException {
        try {
            // Nettoyer le chemin
            String cleanPath = path;
            if (cleanPath.contains("?")) {
                cleanPath = cleanPath.substring(0, cleanPath.indexOf("?"));
            }
            
            // Décoder l'URL
            cleanPath = java.net.URLDecoder.decode(cleanPath, "UTF-8");
            
            // Vérifier si c'est une requête pour l'émulateur relax
            if (cleanPath.startsWith("/relax/")) {
                serveRelaxFile(outputStream, cleanPath, method, enableSharedArrayBuffer);
                return;
            }
            
            // Vérifier si c'est une requête pour la bibliothèque de jeux
            if (cleanPath.startsWith("/gamelibrary/")) {
                serveGameLibraryFile(outputStream, cleanPath, method, enableSharedArrayBuffer);
                return;
            }
            
            // Vérifier si c'est une requête pour les données partagées (GameLibrary-Data)
            if (cleanPath.startsWith("/gamedata/")) {
                serveGameDataFile(outputStream, cleanPath, method, enableSharedArrayBuffer);
                return;
            }

            
            // Construire le chemin complet
            Path filePath = Paths.get(SITES_DIR, cleanPath.substring(1)); // Enlever le premier /
            
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
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur service fichier: " + path, e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * Sert les fichiers de l'émulateur relax depuis les assets
     */
    private void serveRelaxFile(OutputStream outputStream, String path, String method, boolean enableSharedArrayBuffer) throws IOException {
        try {
            // Extraire le chemin du fichier (enlever /relax/)
            String assetPath = path.substring(7); // Enlever "/relax/"
            
            // Ouvrir le fichier depuis les assets
            InputStream inputStream = context.getAssets().open("relax/" + assetPath);
            
            // Déterminer le type de contenu
            String contentType = getContentType(assetPath);
            
            // Lire le contenu du fichier
            byte[] content = new byte[inputStream.available()];
            inputStream.read(content);
            inputStream.close();
            
            // Envoyer la réponse HTTP avec headers SharedArrayBuffer conditionnels
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + contentType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            getSharedArrayBufferHeaders(enableSharedArrayBuffer) +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            
            // Pour les requêtes HEAD, ne pas envoyer le contenu
            if (!"HEAD".equals(method)) {
                outputStream.write(content);
            }
            outputStream.flush();
            
            Log.d(TAG, "Served relax file: " + assetPath);
            
        } catch (IOException e) {
            Log.e(TAG, "Erreur lors du service du fichier relax: " + path, e);
            sendErrorResponse(outputStream, 404, "Not Found");
        }
    }
    
    /**
     * Sert les fichiers depuis GameLibrary-Data (répertoire partagé)
     * Utilisé par la route /gamedata/
     */
    private void serveGameDataFile(OutputStream outputStream, String path, String method, boolean enableSharedArrayBuffer) throws IOException {
        try {
            // Extraire le chemin du fichier (enlever /gamedata/)
            String filePath = path.substring(10); // Enlever "/gamedata/"
            
            // Décoder l'URL pour gérer les caractères spéciaux encodés (%2B → +, etc.)
            try {
                filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
            } catch (Exception e) {
                Log.w(TAG, "Failed to decode URL: " + filePath);
            }
            
            // Cas spécial: auto-générer gamelist.json si demandé mais absent
            if (filePath.endsWith("/gamelist.json")) {
                // Extraire le chemin de la console (ex: "fbneo" ou "fbneo/sega")
                String consoleName = filePath.substring(0, filePath.lastIndexOf("/gamelist.json"));
                String fullPath = "/storage/emulated/0/GameLibrary-Data/" + filePath;
                File file = new File(fullPath);
                
                if (!file.exists()) {
                    Log.i(TAG, "gamelist.json not found via /gamedata/, generating automatically for: " + consoleName);
                    serveAutoGeneratedGamelist(outputStream, consoleName, method);
                    return;
                }
            }
            
            // Construire le chemin complet vers GameLibrary-Data
            String fullPath = "/storage/emulated/0/GameLibrary-Data/" + filePath;
            File file = new File(fullPath);
            
            if (!file.exists()) {
                Log.w(TAG, "File not found in GameLibrary-Data: " + fullPath);
                sendErrorResponse(outputStream, 404, "Not Found");
                return;
            }
            
            if (file.isDirectory()) {
                // Pour les répertoires, retourner un listing HTML simple
                serveDirectoryListing(outputStream, file, path);
                return;
            }
            
            // Déterminer le type de contenu
            String contentType = getContentType(filePath);
            
            // Envoyer la réponse HTTP avec headers SharedArrayBuffer conditionnels
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + file.length() + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    getSharedArrayBufferHeaders(enableSharedArrayBuffer) +
                    "\r\n";
            
            outputStream.write(response.getBytes());
            
            // Pour les requêtes HEAD, ne pas envoyer le contenu
            if (!"HEAD".equals(method)) {
                // Streaming avec buffer 64KB pour les gros fichiers (ROMs PSX/Sega CD 450+ MB)
                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] buffer = new byte[65536]; // 64KB buffer pour performance
                int bytesRead;
                
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                fileInputStream.close();
            }
            outputStream.flush();
            
            Log.d(TAG, "Served GameLibrary-Data file: " + fullPath);
            
        } catch (IOException e) {
            Log.e(TAG, "Error serving GameLibrary-Data file: " + path, e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * Génère automatiquement un gamelist.json à partir des ROMs du répertoire
     */
    private void serveAutoGeneratedGamelist(OutputStream outputStream, String consoleName, String method) throws IOException {
        try {
            String consolePath = "/storage/emulated/0/GameLibrary-Data/" + consoleName;
            File consoleDir = new File(consolePath);
            
            if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                sendErrorResponse(outputStream, 404, "Console directory not found");
                return;
            }
            
            // Scanner les ROMs
            File[] files = consoleDir.listFiles();
            org.json.JSONArray gamesArray = new org.json.JSONArray();
            int gameId = 1;
            
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && isRomFileForAutoGen(file.getName())) {
                        String fileName = file.getName();
                        String baseName = getBaseNameFromFile(fileName);
                        
                        org.json.JSONObject game = new org.json.JSONObject();
                        game.put("id", String.valueOf(gameId++));
                        game.put("name", normalizeDisplayName(baseName)); // Normalisé pour affichage
                        game.put("path", "./" + fileName); // Original pour les images
                        game.put("desc", "Custom ROM - No description available");
                        game.put("image", "./media/box2d/" + baseName + ".png");
                        game.put("screenshot", "./media/screenshots/" + baseName + ".png");
                        game.put("thumbnail", "./media/box2d/" + baseName + ".png");
                        game.put("releasedate", "Unknown");
                        game.put("genre", "Custom");
                        game.put("players", "1-2");
                        
                        gamesArray.put(game);
                    }
                }
            }
            
            // Créer le gamelist.json
            org.json.JSONObject gamelist = new org.json.JSONObject();
            gamelist.put("games", gamesArray);
            
            String jsonContent = gamelist.toString(2);
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: " + jsonContent.length() + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            
            if (!"HEAD".equals(method)) {
                outputStream.write(jsonContent.getBytes("UTF-8"));
            }
            outputStream.flush();
            
            Log.i(TAG, "Served auto-generated gamelist.json for " + consoleName + " with " + gamesArray.length() + " games");
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating gamelist.json", e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * Vérifie si un fichier est un ROM (pour la génération automatique)
     */
    private boolean isRomFileForAutoGen(String fileName) {
        String lowerName = fileName.toLowerCase();
        String[] romExtensions = {
            ".nes", ".fds", ".smc", ".sfc", ".n64", ".z64", ".v64",
            ".md", ".gen", ".smd", ".gb", ".gbc", ".gba", ".nds",
            ".iso", ".cue", ".bin", ".img", ".pbp", ".cso", ".PBP", ".chd",
            ".zip", ".7z", ".rar", ".rom", ".gg", ".sms"
        };
        
        for (String ext : romExtensions) {
            if (lowerName.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extrait le nom de base d'un fichier (sans extension)
     */
    private String getBaseNameFromFile(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
    
    /**
     * Normalise le nom pour l'affichage dans le gamelist.json
     * Exemple: "Game (U) (V1.2) [!]" → "Game (USA)"
     */
    private String normalizeDisplayName(String romName) {
        String normalized = romName;
        
        // Remplacer les codes région courts par les noms complets
        normalized = normalized.replaceAll("\\(U\\)", "(USA)");
        normalized = normalized.replaceAll("\\(E\\)", "(Europe)");
        normalized = normalized.replaceAll("\\(J\\)", "(Japan)");
        normalized = normalized.replaceAll("\\(W\\)", "(World)");
        normalized = normalized.replaceAll("\\(UE\\)", "(USA, Europe)");
        normalized = normalized.replaceAll("\\(JU\\)", "(Japan, USA)");
        
        // Enlever tout après la première région: (V...), [!], [h...], etc.
        // Trouve la première parenthèse fermée (fin de région)
        int firstCloseParen = normalized.indexOf(')');
        if (firstCloseParen > 0 && firstCloseParen < normalized.length() - 1) {
            String afterRegion = normalized.substring(firstCloseParen + 1);
            // Si ce qui suit commence par " (" ou " [", on coupe
            if (afterRegion.startsWith(" (") || afterRegion.startsWith(" [")) {
                normalized = normalized.substring(0, firstCloseParen + 1);
            }
        }
        
        // Enlever les tags entre crochets qui restent
        normalized = normalized.replaceAll("\\s*\\[.*?\\]", "");
        
        return normalized.trim();
    }
    
    /**
     * Sert un listing simple d'un répertoire (pour le scanner AUTO SCAN)
     */
    private void serveDirectoryListing(OutputStream outputStream, File directory, String path) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>Directory Listing</title></head><body>\n");
        html.append("<h1>Directory: ").append(path).append("</h1>\n");
        html.append("<ul>\n");
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    html.append("<li><a href=\"").append(file.getName()).append("\">")
                        .append(file.getName()).append("</a></li>\n");
                }
            }
        }
        
        html.append("</ul>\n</body></html>");
        
        String htmlContent = html.toString();
        String response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: " + htmlContent.length() + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type\r\n" +
                        "\r\n";
        
        outputStream.write(response.getBytes());
        outputStream.write(htmlContent.getBytes());
        outputStream.flush();
        
        Log.d(TAG, "Served directory listing: " + path);
    }
    
    /**
     * Sert les fichiers de la bibliothèque de jeux depuis les assets
     */
    private void serveGameLibraryFile(OutputStream outputStream, String path, String method, boolean enableSharedArrayBuffer) throws IOException {
        try {
            // Extraire le chemin du fichier (enlever /gamelibrary/)
            String assetPath = path.substring(13); // Enlever "/gamelibrary/"
            
            // Si vide ou juste "/", afficher le listing des consoles
            if (assetPath.isEmpty() || assetPath.equals("/")) {
                serveConsolesListingPage(outputStream, method);
                return;
            }
            
            // Gestion spéciale pour les répertoires unified/ et games/
            if (assetPath.equals("unified/") || assetPath.equals("games/")) {
                serveGameLibraryDirectory(outputStream, assetPath);
                return;
            }
            
            // Gestion spéciale pour les consoles - servir depuis le stockage externe
            // Detecter automatiquement si c'est un repertoire de console
            if (assetPath.contains("/") && !assetPath.startsWith("data/") && !assetPath.startsWith("emulatorjs/")) {
                String potentialConsole = assetPath.substring(0, assetPath.indexOf('/'));
                // Verifier si c'est un repertoire de console valide
                if (isConsoleDirectory(potentialConsole)) {
                    serveConsoleFile(outputStream, assetPath, method, enableSharedArrayBuffer);
                    return;
                }
            }
            
            // API endpoint pour lister les consoles disponibles
            if (assetPath.equals("api/consoles")) {
                serveConsolesAPI(outputStream, method);
                return;
            }
            
            // Gestion spéciale pour gamelist.json - servir depuis les assets (ancien système)
            if (assetPath.equals("gamelist.json")) {
                serveGamelistJson(outputStream, method);
                return;
            }
            
            // Pour tous les fichiers HTML et JS, essayer de charger depuis le stockage d'abord
            if (assetPath.endsWith(".html") || assetPath.endsWith(".js")) {
                if (tryServeFromStorage(outputStream, assetPath, method, enableSharedArrayBuffer)) {
                    return; // Fichier servi depuis le stockage
                }
                // Sinon, continuer pour servir depuis les assets
            }
            
            // Construire le chemin complet vers l'asset
            String fullAssetPath = "gamelibrary/" + assetPath;
            
            // Ouvrir le fichier depuis les assets
            InputStream inputStream = context.getAssets().open(fullAssetPath);
            
            // Déterminer le type de contenu
            String contentType = getContentType(assetPath);
            
            // Lire le contenu du fichier
            byte[] content = new byte[inputStream.available()];
            inputStream.read(content);
            inputStream.close();
            
            // Envoyer la réponse HTTP avec headers SharedArrayBuffer conditionnels
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + contentType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            getSharedArrayBufferHeaders(enableSharedArrayBuffer) +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            
            // Pour les requêtes HEAD, ne pas envoyer le contenu
            if (!"HEAD".equals(method)) {
                outputStream.write(content);
            }
            outputStream.flush();
            
            Log.d(TAG, "Served game library file: " + fullAssetPath);

        } catch (IOException e) {
            Log.e(TAG, "Erreur lors du service du fichier game library: " + path, e);
            sendErrorResponse(outputStream, 404, "Not Found");
        }
    }
    
    /**
     * Verifie si un repertoire est un repertoire de console valide
     * Accepte les repertoires avec gamelist.json OU contenant des fichiers ROM
     */
    private boolean isConsoleDirectory(String dirName) {
        // Ignorer les repertoires systeme
        if (dirName.equals("data") || dirName.equals("emulatorjs") || dirName.equals("vmnes") || dirName.equals("playlists")) {
            return false;
        }
        
        // Verifier si le repertoire existe dans GameLibrary-Data
        java.io.File consoleDir = new java.io.File("/storage/emulated/0/GameLibrary-Data/" + dirName);
        if (!consoleDir.exists() || !consoleDir.isDirectory()) {
            return false;
        }
        
        // Si gamelist.json existe, c'est valide
        java.io.File gamelistFile = new java.io.File(consoleDir, "gamelist.json");
        if (gamelistFile.exists()) {
            return true;
        }
        
        // Sinon, vérifier s'il contient des fichiers ROM (console custom)
        return hasRomFiles(consoleDir);
    }
    
    /**
     * Vérifie si un répertoire contient des fichiers ROM
     */
    private boolean hasRomFiles(java.io.File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        
        String[] romExtensions = {
            // Nintendo
            ".nes", ".fds", ".unf",                           // NES/Famicom
            ".smc", ".sfc", ".fig",                           // SNES
            ".n64", ".z64", ".v64",                           // N64
            ".gb", ".gbc",                                    // Game Boy / Color
            ".gba", ".agb",                                   // Game Boy Advance
            ".nds", ".dsi",                                   // Nintendo DS
            ".3ds", ".3dsx", ".cia",                          // Nintendo 3DS
            
            // Sega
            ".md", ".gen", ".smd", ".32x",                    // Genesis/Mega Drive
            ".gg",                                            // Game Gear
            ".sms",                                           // Master System
            ".cdi", ".gdi",                                   // Dreamcast
            ".sat",                                           // Saturn
            
            // Sony
            ".iso", ".cue", ".bin", ".img", ".mdf", ".chd",   // PS1/PS2/etc + CHD
            ".pbp", ".cso",                                   // PSP
            
            // Atari
            ".a26", ".a52", ".a78",                           // Atari 2600/5200/7800
            ".st", ".stx",                                    // Atari ST
            ".xex", ".atr", ".bin",                           // Atari 8-bit
            
            // Other
            ".pce", ".sgx",                                   // PC Engine / TurboGrafx-16
            ".ngp", ".ngc",                                   // Neo Geo Pocket
            ".ws", ".wsc",                                    // WonderSwan
            ".lnx",                                           // Atari Lynx
            ".vec",                                           // Vectrex
            ".int",                                           // Intellivision
            ".col",                                           // ColecoVision
            ".min",                                           // Pokemon Mini
            ".vb",                                            // Virtual Boy
            ".d64", ".t64", ".prg",                          // Commodore 64
            ".dsk", ".adf",                                   // Amiga / Amstrad
            
            // Archives
            ".zip", ".7z", ".rar", ".gz",                     // Compressed files
            ".rom"                                            // Generic ROM
        };
        
        java.io.File[] files = dir.listFiles();
        if (files == null) return false;
        
        for (java.io.File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                for (String ext : romExtensions) {
                    if (fileName.endsWith(ext)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Sert les fichiers depuis les répertoires console du stockage externe
     * (ROMs, gamelist.json, images, etc.)
     * Supporte automatiquement toute console ayant un gamelist.json
     */
    /**
     * Essayer de servir un fichier HTML depuis le stockage interne
     * Retourne true si le fichier a été servi, false sinon
     */
    private boolean tryServeFromStorage(OutputStream outputStream, String fileName, String method, boolean enableSharedArrayBuffer) {
        try {
            // Chemin vers le fichier personnalisé dans le stockage (RetroPlay-Files/sites/gamelibrary/)
            String storagePath = "/storage/emulated/0/RetroPlay-Files/sites/gamelibrary/" + fileName;
            File htmlFile = new File(storagePath);
            
            if (htmlFile.exists() && htmlFile.isFile()) {
                Log.d(TAG, "Serving " + fileName + " from storage: " + storagePath);
                
                // Déterminer le Content-Type basé sur l'extension
                String contentType = "text/html; charset=UTF-8";
                if (fileName.endsWith(".js")) {
                    contentType = "application/javascript; charset=UTF-8";
                } else if (fileName.endsWith(".css")) {
                    contentType = "text/css; charset=UTF-8";
                }
                
                // HEAD request - juste envoyer le header avec headers SharedArrayBuffer conditionnels
                if ("HEAD".equals(method)) {
                    String response = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + contentType + "\r\n" +
                                    "Content-Length: " + htmlFile.length() + "\r\n" +
                                    getSharedArrayBufferHeaders(enableSharedArrayBuffer) +
                                    "Connection: close\r\n\r\n";
                    outputStream.write(response.getBytes());
                    outputStream.flush();
                    return true;
                }
                
                // Lire le fichier depuis le stockage
                FileInputStream fis = new FileInputStream(htmlFile);
                byte[] content = new byte[(int) htmlFile.length()];
                fis.read(content);
                fis.close();
                
                // Envoyer la réponse HTTP avec headers SharedArrayBuffer conditionnels
                String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + contentType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                getSharedArrayBufferHeaders(enableSharedArrayBuffer) +
                                "Connection: close\r\n\r\n";
                outputStream.write(response.getBytes());
                outputStream.write(content);
                outputStream.flush();
                
                Log.d(TAG, "Served custom " + fileName + " from storage");
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not serve " + fileName + " from storage: " + e.getMessage());
        }
        
        return false; // Fichier non trouvé ou erreur, utiliser les assets
    }
    
    private void serveConsoleFile(OutputStream outputStream, String assetPath, String method, boolean enableSharedArrayBuffer) throws IOException {
        try {
            // Déterminer la console et le nom du fichier
            // assetPath est du format: "nes/file.zip" ou "snes/gamelist.json" ou "n64/file.z64"
            String console = assetPath.substring(0, assetPath.indexOf('/'));
            String fileName = assetPath.substring(assetPath.indexOf('/') + 1);
            
            // Cas spécial: gamelist.json n'existe pas → Générer automatiquement!
            if (fileName.equals("gamelist.json")) {
                String filePath = "/storage/emulated/0/GameLibrary-Data/" + console + "/gamelist.json";
                java.io.File file = new java.io.File(filePath);
                
                if (!file.exists()) {
                    Log.i(TAG, "gamelist.json not found, generating automatically from ROMs for: " + console);
                    serveAutoGeneratedGamelist(outputStream, console, method);
                    return;
                }
            }
            
            // Construire le chemin complet vers le fichier dans GameLibrary-Data
            String filePath = "/storage/emulated/0/GameLibrary-Data/" + console + "/" + fileName;
            
            Log.d(TAG, "Attempting to serve console file: " + filePath);
            
            // Vérifier si le fichier existe
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "Console file not found: " + filePath);
                sendErrorResponse(outputStream, 404, "Not Found");
                return;
            }
            
            // Déterminer le type de contenu
            String contentType = getContentType(fileName);
            
            // Envoyer la réponse HTTP avec headers SharedArrayBuffer conditionnels
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + contentType + "\r\n" +
                            "Content-Length: " + file.length() + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            getSharedArrayBufferHeaders(enableSharedArrayBuffer) +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            
            // Pour les requêtes HEAD, ne pas envoyer le contenu
            if (!"HEAD".equals(method)) {
                // Streamer le fichier par morceaux (évite OutOfMemoryError pour gros fichiers)
                java.io.FileInputStream fileInputStream = new java.io.FileInputStream(file);
                byte[] buffer = new byte[8192]; // Buffer de 8KB
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();
            }
            outputStream.flush();
            
            Log.d(TAG, "Served console file: " + console + "/" + fileName + " (" + file.length() + " bytes)");

        } catch (IOException e) {
            Log.e(TAG, "Erreur lors du service du fichier console: " + assetPath, e);
            sendErrorResponse(outputStream, 404, "Not Found");
        }
    }
    
    /**
     * Sert une page HTML listant toutes les consoles disponibles
     */
    private void serveConsolesListingPage(OutputStream outputStream, String method) throws IOException {
        try {
            // Scanner les consoles disponibles
            java.io.File gamelibraryDir = new java.io.File("/storage/emulated/0/GameLibrary-Data");
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html><head>\n");
            html.append("<meta charset=\"utf-8\">\n");
            html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            html.append("<title>Game Library - ChatAI</title>\n");
            html.append("<style>\n");
            html.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
            html.append("body { font-family: 'Courier New', monospace; background: #000; color: #ff3333; min-height: 100vh; padding: 20px; }\n");
            html.append(".header { text-align: center; margin-bottom: 40px; }\n");
            html.append("h1 { font-size: 2.5em; color: #ff3333; text-shadow: 2px 2px 4px rgba(0,0,0,0.8); margin-bottom: 10px; }\n");
            html.append(".subtitle { font-size: 1.1em; color: #ff6666; }\n");
            html.append(".consoles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; max-width: 1200px; margin: 0 auto; }\n");
            html.append(".console-card { background: #330000; border-radius: 12px; padding: 20px; border: 2px solid #ff3333; transition: all 0.3s ease; cursor: pointer; text-decoration: none; color: inherit; display: block; }\n");
            html.append(".console-card:hover { transform: translateY(-5px); box-shadow: 0 10px 30px rgba(255, 51, 51, 0.5); border-color: #ff6666; background: #660000; }\n");
            html.append(".console-icon { font-size: 3em; text-align: center; margin-bottom: 15px; }\n");
            html.append(".console-name { font-size: 1.5em; font-weight: bold; text-align: center; margin-bottom: 10px; }\n");
            html.append(".console-info { font-size: 0.9em; color: #ff6666; text-align: center; margin-bottom: 5px; }\n");
            html.append(".badge { display: inline-block; background: #ff6666; color: #000; padding: 4px 8px; border-radius: 4px; font-size: 0.7em; font-weight: bold; margin-top: 10px; }\n");
            html.append("</style>\n");
            html.append("</head><body>\n");
            
            html.append("<div class=\"header\">\n");
            html.append("<h1>🎮 CHATAI GAME LIBRARY</h1>\n");
            html.append("<p class=\"subtitle\">SÉLECTIONNEZ UNE CONSOLE</p>\n");
            html.append("</div>\n");
            
            html.append("<div class=\"consoles-grid\">\n");
            
            if (gamelibraryDir.exists() && gamelibraryDir.isDirectory()) {
                java.io.File[] directories = gamelibraryDir.listFiles(java.io.File::isDirectory);
                
                if (directories != null) {
                    for (java.io.File dir : directories) {
                        String dirName = dir.getName();
                        
                        // Ignorer les répertoires système
                        if (dirName.equals("data") || dirName.equals("emulatorjs") || 
                            dirName.equals("vmnes") || dirName.equals("playlists")) {
                            continue;
                        }
                        
                        // Vérifier si gamelist.json existe OU si le répertoire contient des ROMs
                        java.io.File gamelistFile = new java.io.File(dir, "gamelist.json");
                        boolean hasGamelist = gamelistFile.exists();
                        boolean hasRoms = hasRomFiles(dir);
                        
                        if (hasGamelist || hasRoms) {
                            String fullName = getConsoleFullName(dirName);
                            String color = getConsoleColor(dirName);
                            String defaultCore = getDefaultCore(dirName);
                            
                            html.append("<a href=\"./index.html?console=").append(dirName).append("\" class=\"console-card\" style=\"border-color: ").append(color).append(";\">\n");
                            html.append("<div class=\"console-icon\">🎮</div>\n");
                            html.append("<div class=\"console-name\" style=\"color: ").append(color).append(";\">").append(dirName.toUpperCase()).append("</div>\n");
                            html.append("<div class=\"console-info\">").append(fullName).append("</div>\n");
                            html.append("<div class=\"console-info\">Core: ").append(defaultCore).append("</div>\n");
                            
                            // Badge selon le mode
                            if (!hasGamelist) {
                                html.append("<div style=\"text-align: center;\"><span class=\"badge\" style=\"background: #00ff00; color: #000;\">AUTO SCAN</span></div>\n");
                            } else if (!hasPresetConfig(dirName)) {
                                html.append("<div style=\"text-align: center;\"><span class=\"badge\">AUTO-DETECTED</span></div>\n");
                            }
                            
                            html.append("</a>\n");
                        }
                    }
                }
            }
            
            html.append("</div>\n");
            html.append("</body></html>\n");
            
            String htmlContent = html.toString();
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + htmlContent.getBytes("UTF-8").length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            if (!"HEAD".equals(method)) {
                outputStream.write(htmlContent.getBytes("UTF-8"));
            }
            outputStream.flush();
            
            Log.d(TAG, "Served consoles listing page");

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du service de la page de listing des consoles", e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * API endpoint qui retourne la liste des consoles disponibles
     * Scanne automatiquement les répertoires dans gamelibrary/
     */
    private void serveConsolesAPI(OutputStream outputStream, String method) throws IOException {
        try {
            org.json.JSONArray consolesArray = new org.json.JSONArray();
            
            // Scanner les répertoires dans GameLibrary-Data
            java.io.File gamelibraryDir = new java.io.File("/storage/emulated/0/GameLibrary-Data");
            
            if (gamelibraryDir.exists() && gamelibraryDir.isDirectory()) {
                java.io.File[] directories = gamelibraryDir.listFiles(java.io.File::isDirectory);
                
                if (directories != null) {
                    for (java.io.File dir : directories) {
                        String dirName = dir.getName();
                        
                        // Ignorer les répertoires système
                        if (dirName.equals("data") || dirName.equals("emulatorjs") || dirName.equals("vmnes") || dirName.equals("playlists")) {
                            continue;
                        }
                        
                        // Vérifier si gamelist.json existe OU si le répertoire contient des ROMs
                        java.io.File gamelistFile = new java.io.File(dir, "gamelist.json");
                        boolean hasGamelist = gamelistFile.exists();
                        boolean hasRoms = hasRomFiles(dir);
                        
                        if (hasGamelist || hasRoms) {
                            org.json.JSONObject consoleInfo = detectConsoleConfig(dir, dirName);
                            
                            if (consoleInfo != null) {
                                // Ajouter un flag pour indiquer le mode AUTO SCAN
                                consoleInfo.put("autoScan", !hasGamelist);
                                consolesArray.put(consoleInfo);
                                String mode = hasGamelist ? "gamelist.json" : "AUTO SCAN";
                                Log.d(TAG, "Console detected: " + dirName + " (" + consoleInfo.optString("fullName") + ") - Mode: " + mode);
                            }
                        }
                    }
                }
            }
            
            org.json.JSONObject response = new org.json.JSONObject();
            response.put("consoles", consolesArray);
            
            String jsonContent = response.toString();
            byte[] jsonBytes = jsonContent.getBytes("UTF-8");
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: " + jsonBytes.length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            "\r\n";
            
            outputStream.write(httpResponse.getBytes());
            
            if (!"HEAD".equals(method)) {
                outputStream.write(jsonBytes);
            }
            outputStream.flush();
            
            Log.d(TAG, "Served consoles API: " + consolesArray.length() + " consoles found");

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du service de l'API consoles", e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * Detecte la configuration d'une console selon la strategie hybride:
     * 1. Lire console.json si present
     * 2. Utiliser les presets si disponibles
     * 3. Auto-detecter depuis cores.json
     * 4. Fallback generique
     */
    private org.json.JSONObject detectConsoleConfig(java.io.File consoleDir, String dirName) {
        try {
            org.json.JSONObject consoleInfo = new org.json.JSONObject();
            
            // 1. Essayer de lire console.json dans le repertoire
            java.io.File configFile = new java.io.File(consoleDir, "console.json");
            if (configFile.exists()) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(configFile);
                    byte[] buffer = new byte[(int) configFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String json = new String(buffer, "UTF-8");
                    
                    org.json.JSONObject userConfig = new org.json.JSONObject(json);
                    Log.d(TAG, "Loaded custom console.json for: " + dirName);
                    
                    // Utiliser la config utilisateur et ajouter les champs manquants
                    // Si l'ID est spécifié dans console.json, l'utiliser, sinon utiliser dirName
                    consoleInfo.put("id", userConfig.optString("id", dirName));
                    consoleInfo.put("name", userConfig.optString("name", dirName.toUpperCase()));
                    consoleInfo.put("fullName", userConfig.optString("fullName", dirName.toUpperCase()));
                    consoleInfo.put("directory", dirName);
                    consoleInfo.put("gamelistPath", dirName + "/gamelist.json");
                    consoleInfo.put("cores", userConfig.optJSONArray("cores"));
                    consoleInfo.put("defaultCore", userConfig.optString("defaultCore", "auto"));
                    consoleInfo.put("extensions", userConfig.optJSONArray("extensions"));
                    consoleInfo.put("icon", userConfig.optString("icon", "🎮"));
                    consoleInfo.put("color", userConfig.optString("color", "#FF3333"));
                    consoleInfo.put("enabled", userConfig.optBoolean("enabled", true));
                    
                    return consoleInfo;
                } catch (Exception e) {
                    Log.w(TAG, "Error reading console.json for " + dirName + ", using preset", e);
                }
            }
            
            // 2. Utiliser les presets si disponibles
            if (hasPresetConfig(dirName)) {
                consoleInfo.put("id", dirName);
                consoleInfo.put("name", dirName.toUpperCase());
                consoleInfo.put("fullName", getConsoleFullName(dirName));
                consoleInfo.put("directory", dirName);
                consoleInfo.put("gamelistPath", dirName + "/gamelist.json");
                consoleInfo.put("cores", getConsoleCores(dirName));
                consoleInfo.put("defaultCore", getDefaultCore(dirName));
                consoleInfo.put("extensions", getConsoleExtensions(dirName));
                consoleInfo.put("icon", "🎮");
                consoleInfo.put("color", getConsoleColor(dirName));
                consoleInfo.put("enabled", true);
                
                Log.d(TAG, "Using preset config for: " + dirName);
                return consoleInfo;
            }
            
            // 3. Auto-detecter depuis cores.json (TODO: implementer)
            // Pour l'instant, passer directement au fallback
            
            // 4. Fallback generique
            Log.d(TAG, "Using generic fallback config for: " + dirName);
            consoleInfo.put("id", dirName);
            consoleInfo.put("name", dirName.toUpperCase());
            consoleInfo.put("fullName", dirName.toUpperCase() + " Console");
            consoleInfo.put("directory", dirName);
            consoleInfo.put("gamelistPath", dirName + "/gamelist.json");
            
            // Cores generiques
            org.json.JSONArray genericCores = new org.json.JSONArray();
            genericCores.put("auto");
            consoleInfo.put("cores", genericCores);
            consoleInfo.put("defaultCore", "auto");
            
            // Extensions generiques
            org.json.JSONArray genericExts = new org.json.JSONArray();
            genericExts.put(".zip").put(".rar").put(".7z");
            consoleInfo.put("extensions", genericExts);
            
            consoleInfo.put("icon", "🎮");
            consoleInfo.put("color", "#FF3333");
            consoleInfo.put("enabled", true);
            consoleInfo.put("isGeneric", true); // Marquer comme generique
            
            return consoleInfo;
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting console config for: " + dirName, e);
            return null;
        }
    }
    
    private boolean hasPresetConfig(String consoleId) {
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom":
            case "snes":
            case "sfc":
            case "n64":
            case "gb":
            case "gbc":
            case "gba":
            case "nds":
            case "ds":
            // Sega
            case "genesis":
            case "megadrive":
            case "md":
            case "mastersystem":
            case "sms":
            case "gamegear":
            case "gg":
            case "32x":
            case "sega32x":
            case "segacd":
            case "megacd":
            case "saturn":
            case "dreamcast":
            case "dc":
            // Sony
            case "ps1":
            case "psx":
            case "playstation":
            case "psp":
            // Atari
            case "atari2600":
            case "2600":
            case "atari5200":
            case "5200":
            case "atari7800":
            case "7800":
            case "lynx":
            case "jaguar":
            // Autres
            case "3do":
            case "arcade":
            case "mame":
            case "neogeo":
            case "ngp":
            case "wonderswan":
            case "ws":
            case "wsc":
            case "pcengine":
            case "turbografx":
            case "pce":
            case "virtualboy":
            case "vb":
            case "colecovision":
            case "coleco":
            case "dos":
            case "amiga":
            case "c64":
            case "commodore64":
                return true;
            default:
                return false;
        }
    }
    
    private String getConsoleFullName(String consoleId) {
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom": return "Nintendo Entertainment System";
            case "snes":
            case "sfc": return "Super Nintendo Entertainment System";
            case "n64": return "Nintendo 64";
            case "gb": return "Game Boy";
            case "gbc": return "Game Boy Color";
            case "gba": return "Game Boy Advance";
            case "nds":
            case "ds": return "Nintendo DS";
            
            // Sega
            case "genesis":
            case "megadrive":
            case "md": return "Sega Genesis / Mega Drive";
            case "mastersystem":
            case "sms": return "Sega Master System";
            case "gamegear":
            case "gg": return "Sega Game Gear";
            case "32x":
            case "sega32x": return "Sega 32X";
            case "segacd":
            case "megacd": return "Sega CD / Mega CD";
            case "saturn": return "Sega Saturn";
            case "dreamcast":
            case "dc": return "Sega Dreamcast";
            
            // Sony
            case "ps1":
            case "psx":
            case "playstation": return "PlayStation 1";
            case "psp": return "PlayStation Portable";
            
            // Atari
            case "atari2600":
            case "2600": return "Atari 2600";
            case "atari5200":
            case "5200": return "Atari 5200";
            case "atari7800":
            case "7800": return "Atari 7800";
            case "lynx": return "Atari Lynx";
            case "jaguar": return "Atari Jaguar";
            
            // Autres
            case "3do": return "3DO";
            case "arcade":
            case "mame": return "Arcade (MAME)";
            case "neogeo":
            case "ngp": return "Neo Geo Pocket";
            case "wonderswan":
            case "ws":
            case "wsc": return "WonderSwan";
            case "pcengine":
            case "turbografx":
            case "pce": return "PC Engine / TurboGrafx-16";
            case "virtualboy":
            case "vb": return "Virtual Boy";
            case "colecovision":
            case "coleco": return "ColecoVision";
            case "dos": return "DOS";
            case "amiga": return "Commodore Amiga";
            case "c64":
            case "commodore64": return "Commodore 64";
            
            default: return consoleId.toUpperCase();
        }
    }
    
    private org.json.JSONArray getConsoleCores(String consoleId) throws org.json.JSONException {
        org.json.JSONArray cores = new org.json.JSONArray();
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom":
                cores.put("fceumm").put("nestopia");
                break;
            case "snes":
            case "sfc":
                cores.put("snes9x");
                break;
            case "n64":
                cores.put("parallel_n64").put("mupen64plus_next");
                break;
            case "gb":
                cores.put("gambatte").put("mgba");
                break;
            case "gbc":
                cores.put("gambatte").put("mgba");
                break;
            case "gba":
                cores.put("mgba");
                break;
            case "nds":
            case "ds":
                cores.put("melonds").put("desmume");
                break;
            
            // Sega
            case "genesis":
            case "megadrive":
            case "md":
                cores.put("genesis_plus_gx").put("picodrive");
                break;
            case "mastersystem":
            case "sms":
            case "gamegear":
            case "gg":
                cores.put("genesis_plus_gx").put("smsplus");
                break;
            case "32x":
            case "sega32x":
                cores.put("picodrive");
                break;
            case "segacd":
            case "megacd":
                cores.put("genesis_plus_gx");
                break;
            case "saturn":
                cores.put("yabause");
                break;
            
            // Sony
            case "ps1":
            case "psx":
            case "playstation":
                cores.put("pcsx_rearmed").put("mednafen_psx_hw");
                break;
            case "psp":
                cores.put("ppsspp");
                break;
            
            // Atari
            case "atari2600":
            case "2600":
                cores.put("stella2014");
                break;
            case "atari5200":
            case "5200":
                cores.put("a5200");
                break;
            case "atari7800":
            case "7800":
                cores.put("prosystem");
                break;
            case "lynx":
                cores.put("handy");
                break;
            case "jaguar":
                cores.put("virtualjaguar");
                break;
            
            // Autres
            case "3do":
                cores.put("opera");
                break;
            
            // Arcade
            case "arcade":
                cores.put("fbneo").put("mame2010").put("mame2003_plus").put("mame2003");
                break;
            case "mame":
                cores.put("mame2010").put("mame2003_plus").put("mame2003").put("fbneo");
                break;
            case "fbneo":
                cores.put("fbneo").put("mame2010").put("mame2003_plus");
                break;
            case "fbneo/cps1":
            case "cps1":
                cores.put("fbalpha2012_cps1").put("fbneo").put("mame2010");
                break;
            case "fbneo/cps2":
            case "cps2":
                cores.put("fbalpha2012_cps2").put("fbneo").put("mame2010");
                break;
            case "fbneo/cps3":
            case "fbneo/cpiii":
            case "cps3":
                cores.put("fbneo").put("mame2010");
                break;
            case "fbneo/sega":
                cores.put("fbneo").put("mame2010").put("mame2003_plus");
                break;
            case "fbneo/taito":
                cores.put("fbneo").put("mame2010").put("mame2003_plus");
                break;
            
            case "neogeo":
                cores.put("fbneo");
                break;
            case "ngp":
                cores.put("mednafen_ngp");
                break;
            case "wonderswan":
            case "ws":
            case "wsc":
                cores.put("mednafen_wswan");
                break;
            case "pcengine":
            case "turbografx":
            case "pce":
                cores.put("mednafen_pce");
                break;
            case "virtualboy":
            case "vb":
                cores.put("beetle_vb");
                break;
            case "colecovision":
            case "coleco":
                cores.put("gearcoleco");
                break;
            case "dos":
                cores.put("dosbox_pure");
                break;
            case "amiga":
                cores.put("puae");
                break;
            case "c64":
            case "commodore64":
                cores.put("vice_x64").put("vice_x64sc").put("vice_x128");
                break;
            
            default:
                cores.put("auto");
                break;
        }
        return cores;
    }
    
    private String getDefaultCore(String consoleId) {
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom": return "fceumm";
            case "snes":
            case "sfc": return "snes9x";
            case "n64": return "parallel_n64";
            case "gb": return "gambatte";
            case "gbc": return "gambatte";
            case "gba": return "mgba";
            case "nds":
            case "ds": return "melonds";
            
            // Sega
            case "genesis":
            case "megadrive":
            case "md": return "genesis_plus_gx";
            case "mastersystem":
            case "sms": return "genesis_plus_gx";
            case "gamegear":
            case "gg": return "genesis_plus_gx";
            case "32x":
            case "sega32x": return "picodrive";
            case "segacd":
            case "megacd": return "genesis_plus_gx";
            case "saturn": return "yabause";
            
            // Sony
            case "ps1":
            case "psx":
            case "playstation": return "pcsx_rearmed";
            case "psp": return "ppsspp";
            
            // Atari
            case "atari2600":
            case "2600": return "stella2014";
            case "atari5200":
            case "5200": return "a5200";
            case "atari7800":
            case "7800": return "prosystem";
            case "lynx": return "handy";
            case "jaguar": return "virtualjaguar";
            
            // Autres
            case "3do": return "opera";
            
            // Arcade
            case "arcade": return "fbneo";
            case "mame": return "mame2010";
            case "fbneo": return "fbneo";
            case "fbneo/cps1":
            case "cps1": return "fbalpha2012_cps1";
            case "fbneo/cps2":
            case "cps2": return "fbalpha2012_cps2";
            case "fbneo/cps3":
            case "fbneo/cpiii":
            case "cps3": return "fbneo";
            case "fbneo/sega": return "fbneo";
            case "fbneo/taito": return "fbneo";
            
            case "neogeo": return "fbneo";
            case "ngp": return "mednafen_ngp";
            case "wonderswan":
            case "ws":
            case "wsc": return "mednafen_wswan";
            case "pcengine":
            case "turbografx":
            case "pce": return "mednafen_pce";
            case "virtualboy":
            case "vb": return "beetle_vb";
            case "colecovision":
            case "coleco": return "gearcoleco";
            case "dos": return "dosbox_pure";
            case "amiga": return "puae";
            case "c64":
            case "commodore64": return "vice_x64";
            
            default: return "auto";
        }
    }
    
    private org.json.JSONArray getConsoleExtensions(String consoleId) throws org.json.JSONException {
        org.json.JSONArray extensions = new org.json.JSONArray();
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom":
                extensions.put(".nes").put(".fds").put(".unif").put(".unf").put(".zip").put(".rar");
                break;
            case "snes":
            case "sfc":
                extensions.put(".smc").put(".sfc").put(".swc").put(".fig").put(".bs").put(".st").put(".zip").put(".rar");
                break;
            case "n64":
                extensions.put(".n64").put(".v64").put(".z64").put(".bin").put(".u1").put(".ndd").put(".zip").put(".rar");
                break;
            case "gb":
                extensions.put(".gb").put(".dmg").put(".zip").put(".rar");
                break;
            case "gbc":
                extensions.put(".gbc").put(".zip").put(".rar");
                break;
            case "gba":
                extensions.put(".gba").put(".zip").put(".rar");
                break;
            case "nds":
            case "ds":
                extensions.put(".nds").put(".zip").put(".rar");
                break;
            
            // Sega
            case "genesis":
            case "megadrive":
            case "md":
                extensions.put(".md").put(".gen").put(".smd").put(".bin").put(".68k").put(".sgd").put(".zip").put(".rar");
                break;
            case "mastersystem":
            case "sms":
                extensions.put(".sms").put(".zip").put(".rar");
                break;
            case "gamegear":
            case "gg":
                extensions.put(".gg").put(".zip").put(".rar");
                break;
            case "32x":
            case "sega32x":
                extensions.put(".32x").put(".zip").put(".rar");
                break;
            case "segacd":
            case "megacd":
                extensions.put(".cue").put(".iso").put(".chd").put(".zip");
                break;
            case "saturn":
                extensions.put(".cue").put(".iso").put(".ccd").put(".mds").put(".chd").put(".zip");
                break;
            
            // Sony
            case "ps1":
            case "psx":
            case "playstation":
                extensions.put(".cue").put(".toc").put(".m3u").put(".ccd").put(".exe").put(".pbp").put(".chd").put(".zip");
                break;
            case "psp":
                extensions.put(".elf").put(".iso").put(".cso").put(".prx").put(".pbp").put(".zip");
                break;
            
            // Atari
            case "atari2600":
            case "2600":
                extensions.put(".a26").put(".bin").put(".zip");
                break;
            case "atari5200":
            case "5200":
                extensions.put(".a52").put(".bin").put(".zip");
                break;
            case "atari7800":
            case "7800":
                extensions.put(".a78").put(".bin").put(".zip");
                break;
            case "lynx":
                extensions.put(".lnx").put(".zip");
                break;
            case "jaguar":
                extensions.put(".j64").put(".jag").put(".rom").put(".abs").put(".cof").put(".bin").put(".prg").put(".zip");
                break;
            
            // Autres
            case "3do":
                extensions.put(".iso").put(".cue").put(".chd").put(".zip");
                break;
            case "arcade":
            case "mame":
                extensions.put(".zip").put(".7z");
                break;
            case "neogeo":
            case "ngp":
                extensions.put(".ngp").put(".ngc").put(".zip");
                break;
            case "wonderswan":
            case "ws":
            case "wsc":
                extensions.put(".ws").put(".wsc").put(".pc2").put(".zip");
                break;
            case "pcengine":
            case "turbografx":
            case "pce":
                extensions.put(".pce").put(".cue").put(".ccd").put(".iso").put(".img").put(".bin").put(".chd").put(".zip");
                break;
            case "virtualboy":
            case "vb":
                extensions.put(".vb").put(".vboy").put(".bin").put(".zip");
                break;
            case "colecovision":
            case "coleco":
                extensions.put(".col").put(".cv").put(".bin").put(".rom").put(".zip");
                break;
            case "dos":
                extensions.put(".conf").put(".zip");
                break;
            case "amiga":
                extensions.put(".adf").put(".adz").put(".dms").put(".fdi").put(".ipf").put(".hdf").put(".hdz").put(".lha").put(".zip").put(".7z");
                break;
            case "c64":
            case "commodore64":
                extensions.put(".d64").put(".d71").put(".d81").put(".t64").put(".tap").put(".prg").put(".crt").put(".bin").put(".zip");
                break;
            
            default:
                extensions.put(".zip").put(".rar").put(".7z");
                break;
        }
        return extensions;
    }
    
    private String getConsoleColor(String consoleId) {
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom": return "#E30B5C";
            case "snes":
            case "sfc": return "#8B5CF6";
            case "n64": return "#3B82F6";
            case "gb": return "#6B7280";
            case "gbc": return "#F59E0B";
            case "gba": return "#10B981";
            case "nds":
            case "ds": return "#059669";
            
            // Sega
            case "genesis":
            case "megadrive":
            case "md": return "#0066CC";
            case "mastersystem":
            case "sms": return "#DC2626";
            case "gamegear":
            case "gg": return "#7C3AED";
            case "32x": return "#6366F1";
            case "segacd":
            case "megacd": return "#2563EB";
            case "saturn": return "#1E40AF";
            
            // Sony
            case "ps1":
            case "psx":
            case "playstation": return "#6366F1";
            case "psp": return "#4F46E5";
            
            // Atari
            case "atari2600":
            case "2600": return "#DC2626";
            case "atari5200":
            case "5200": return "#EF4444";
            case "atari7800":
            case "7800": return "#F87171";
            case "lynx": return "#FB923C";
            case "jaguar": return "#F59E0B";
            
            // Autres
            case "3do": return "#8B5CF6";
            case "arcade":
            case "mame": return "#EC4899";
            case "neogeo":
            case "ngp": return "#F43F5E";
            case "wonderswan":
            case "ws":
            case "wsc": return "#A855F7";
            case "pcengine":
            case "turbografx":
            case "pce": return "#F97316";
            case "virtualboy":
            case "vb": return "#DC2626";
            case "colecovision":
            case "coleco": return "#0891B2";
            case "dos": return "#64748B";
            case "amiga": return "#DC2626";
            case "c64":
            case "commodore64": return "#7C2D12";
            
            default: return "#FF3333";
        }
    }
    
    /**
     * Sert le fichier gamelist.json depuis les assets
     */
    private void serveGamelistJson(OutputStream outputStream, String method) throws IOException {
        try {
            // Ouvrir le fichier gamelist.json depuis les assets
            InputStream inputStream = context.getAssets().open("gamelist.json");
            
            // Lire le contenu du fichier
            byte[] content = new byte[inputStream.available()];
            inputStream.read(content);
            inputStream.close();
            
            // Envoyer la réponse HTTP
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            
            // Pour les requêtes HEAD, ne pas envoyer le contenu
            if (!"HEAD".equals(method)) {
                outputStream.write(content);
            }
            outputStream.flush();
            
            Log.d(TAG, "Served gamelist.json");

        } catch (IOException e) {
            Log.e(TAG, "Erreur lors du service du fichier gamelist.json", e);
            sendErrorResponse(outputStream, 404, "Not Found");
        }
    }
    
    /**
     * Sert un directory listing pour les répertoires unified/ et games/ de gamelibrary
     */
    private void serveGameLibraryDirectory(OutputStream outputStream, String directoryPath) throws IOException {
        try {
            // Lister les fichiers dans le répertoire assets/gamelibrary/
            String[] files = context.getAssets().list("gamelibrary/" + directoryPath);
            
            if (files == null || files.length == 0) {
                sendErrorResponse(outputStream, 404, "Directory not found");
                return;
            }
            
            // Générer le HTML du directory listing
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html><head><title>Index of /gamelibrary/").append(directoryPath).append("</title>\n");
            html.append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:20px;background:#1a1a1a;color:#fff;}")
                .append("h1{color:#ff6b35;border-bottom:2px solid #ff6b35;padding-bottom:10px;}")
                .append("table{width:100%;border-collapse:collapse;margin-top:20px;}")
                .append("th,td{padding:12px;text-align:left;border-bottom:1px solid #333;}")
                .append("th{background-color:#333;color:#ff6b35;font-weight:bold;}")
                .append("tr:hover{background-color:#2a2a2a;}")
                .append("a{text-decoration:none;color:#4CAF50;font-weight:bold;}")
                .append("a:hover{text-decoration:underline;color:#66ff66;}")
                .append(".file-icon{color:#ff6b35;margin-right:8px;}")
                .append(".back-link{color:#ff6b35;font-size:14px;margin-bottom:20px;display:inline-block;}")
                .append("</style>\n");
            html.append("</head><body>\n");
            html.append("<h1>📁 Game Library - ").append(directoryPath).append("</h1>\n");
            html.append("<a href=\"/gamelibrary/\" class=\"back-link\">← Back to Game Library</a>\n");
            html.append("<table>\n");
            html.append("<thead><tr><th>Name</th><th>Type</th><th>Size</th></tr></thead>\n");
            html.append("<tbody>\n");
            
            // Ajouter les fichiers
            for (String file : files) {
                String fileUrl = "/gamelibrary/" + directoryPath + file;
                String fileIcon = getFileIcon(file);
                String fileType = getFileType(file);
                
                html.append("<tr>\n");
                html.append("<td><span class=\"file-icon\">").append(fileIcon).append("</span>");
                html.append("<a href=\"").append(fileUrl).append("\">").append(file).append("</a></td>\n");
                html.append("<td>").append(fileType).append("</td>\n");
                html.append("<td>-</td>\n"); // Taille non disponible pour les assets
                html.append("</tr>\n");
            }
            
            html.append("</tbody></table>\n");
            html.append("</body></html>\n");
            
            String htmlContent = html.toString();
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: " + htmlContent.length() + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "\r\n";
            
            outputStream.write(response.getBytes());
            outputStream.write(htmlContent.getBytes());
            outputStream.flush();
            
            Log.d(TAG, "Served game library directory listing: " + directoryPath);

        } catch (IOException e) {
            Log.e(TAG, "Erreur lors du listing du répertoire game library: " + directoryPath, e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }
    
    /**
     * Retourne l'icône appropriée selon l'extension du fichier
     */
    private String getFileIcon(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "nes": return "🎮";
            case "rom": return "🎮";
            case "zip": return "📦";
            case "js": return "📜";
            case "html": return "🌐";
            case "css": return "🎨";
            case "json": return "📋";
            case "png": case "jpg": case "jpeg": case "gif": return "🖼️";
            case "txt": return "📄";
            case "md": return "📝";
            default: return "📄";
        }
    }
    
    /**
     * Retourne le type de fichier selon l'extension
     */
    private String getFileType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "nes": return "NES Game";
            case "rom": return "ROM File";
            case "zip": return "Archive";
            case "js": return "JavaScript";
            case "html": return "Web Page";
            case "css": return "Stylesheet";
            case "json": return "JSON Data";
            case "png": case "jpg": case "jpeg": case "gif": return "Image";
            case "txt": return "Text File";
            case "md": return "Markdown";
            default: return "File";
        }
    }
    
    /**
     * Détermine le type de contenu basé sur l'extension du fichier
     */
    private String getContentType(String filename) {
        if (filename.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (filename.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        } else if (filename.endsWith(".json")) {
            return "application/json; charset=utf-8";
        } else if (filename.endsWith(".nes")) {
            return "application/octet-stream";
        } else if (filename.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream";
        }
    }
    
    /**
     * Ajoute les headers CORS et SharedArrayBuffer requis pour PPSSPP
     */
    private void addCorsAndSharedArrayBufferHeaders(StringBuilder headers) {
        // CORS headers only (COEP/COOP removed - they block cross-origin resources and don't work in WebView)
        headers.append("Access-Control-Allow-Origin: *\r\n");
    }
    
    /**
     * Sert un fichier statique avec gestion directe des bytes
     */
    private void serveStaticFile(OutputStream outputStream, Path filePath) throws IOException {
        // Lire le fichier en bytes
        byte[] fileBytes = Files.readAllBytes(filePath);
        
        // Déterminer le type MIME
        String mimeType = getMimeType(filePath.getFileName().toString());
        
        // Headers HTTP
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 200 OK\r\n");
        headers.append("Content-Type: ").append(mimeType).append("\r\n");
        headers.append("Content-Length: ").append(fileBytes.length).append("\r\n");
        headers.append("Server: ChatAI-WebServer/1.0 (Android)\r\n");
        addCorsAndSharedArrayBufferHeaders(headers);
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
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><title>Index of ").append(urlPath).append("</title>\n");
        
        // CSS personnalisé (comme Apache)
        if (!customCSS.isEmpty()) {
            html.append("<style>").append(customCSS).append("</style>\n");
        } else {
            // CSS par défaut
            html.append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:20px;}")
                .append("table{width:100%;border-collapse:collapse;}")
                .append("th,td{padding:8px;text-align:left;border-bottom:1px solid #ddd;}")
                .append("th{background-color:#f2f2f2;}")
                .append("a{text-decoration:none;color:#0066cc;}")
                .append("a:hover{text-decoration:underline;}")
                .append("</style>\n");
        }
        
        html.append("</head><body>\n");
        html.append("<h1>Index of ").append(urlPath).append("</h1>\n");
        html.append("<hr>\n");
        html.append("<table>\n");
        html.append("<tr><th>Name</th><th>Last modified</th><th>Size</th></tr>\n");
        
        try {
            // Tri selon les options (comme Apache/Nginx)
            java.util.List<Path> files = new java.util.ArrayList<>();
            Files.list(dirPath).forEach(files::add);
            
            if (foldersFirst) {
                files.sort((a, b) -> {
                    boolean aIsDir = Files.isDirectory(a);
                    boolean bIsDir = Files.isDirectory(b);
                    if (aIsDir && !bIsDir) return -1;
                    if (!aIsDir && bIsDir) return 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                });
            } else {
                files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            }
            
            files.forEach(file -> {
                try {
                    String name = file.getFileName().toString();
                    String lastModified = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(new Date(Files.getLastModifiedTime(file).toMillis()));
                    long size = Files.isDirectory(file) ? -1 : Files.size(file);
                    
                    html.append("<tr><td>");
                    if (Files.isDirectory(file)) {
                        html.append("<a href=\"").append(name).append("/\">").append(name).append("/</a>");
                    } else {
                        html.append("<a href=\"").append(name).append("\">").append(name).append("</a>");
                    }
                    html.append("</td><td>").append(lastModified).append("</td><td>");
                    if (size == -1) {
                        html.append("-");
                    } else {
                        if (exactSize) {
                            html.append(size).append(" bytes");
                        } else {
                            html.append(formatFileSize(size));
                        }
                    }
                    html.append("</td></tr>\n");
                } catch (IOException e) {
                    Log.e(TAG, "Erreur listing file: " + file, e);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Erreur listing directory: " + dirPath, e);
        }
        
        html.append("</table>\n");
        html.append("<hr>\n");
        
        // Pied de page avec infos serveur (comme Apache/Nginx)
        html.append("<address>ChatAI WebServer/1.0 (Android) Server at ")
            .append("localhost Port ").append(PORT).append("</address>\n");
        html.append("</body></html>\n");
        
        byte[] htmlBytes = html.toString().getBytes("UTF-8");
        
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
    }
    
    /**
     * Génère les headers SharedArrayBuffer conditionnellement
     */
    private String getSharedArrayBufferHeaders(boolean enable) {
        if (enable) {
            return "Cross-Origin-Embedder-Policy: require-corp\r\n" +
                   "Cross-Origin-Opener-Policy: same-origin\r\n" +
                   "Cross-Origin-Resource-Policy: cross-origin\r\n";
        }
        return "";
    }
    
    /**
     * Envoie une réponse de redirection (302 Found)
     */
    private void sendRedirectResponse(OutputStream outputStream, String location) throws IOException {
        String html = "<!DOCTYPE html><html><head>" +
                     "<meta http-equiv=\"refresh\" content=\"0;url=" + location + "\">" +
                     "</head><body>" +
                     "<h2>Redirection vers localhost...</h2>" +
                     "<p>Pour activer SharedArrayBuffer (requis pour PSP), redirection vers <a href=\"" + location + "\">localhost</a></p>" +
                     "<script>window.location.href='" + location + "';</script>" +
                     "</body></html>";
        
        String response = "HTTP/1.1 302 Found\r\n" +
                       "Location: " + location + "\r\n" +
                       "Content-Type: text/html; charset=utf-8\r\n" +
                       "Content-Length: " + html.getBytes("UTF-8").length + "\r\n" +
                       "\r\n" + html;
        outputStream.write(response.getBytes("UTF-8"));
        outputStream.flush();
    }
    
    /**
     * Envoie une réponse d'erreur
     */
    private void sendErrorResponse(OutputStream outputStream, int statusCode, String message) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + message + "\r\n" +
                       "Content-Type: text/plain\r\n" +
                       "Content-Length: " + message.length() + "\r\n" +
                       "\r\n" + message;
        outputStream.write(response.getBytes("UTF-8"));
        outputStream.flush();
    }
    
    /**
     * Détermine le type MIME d'un fichier
     */
    private String getMimeType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        switch (extension) {
            case "html":
            case "htm":
                return "text/html; charset=utf-8";
            case "css":
                return "text/css; charset=utf-8";
            case "js":
                return "application/javascript; charset=utf-8";
            case "json":
                return "application/json; charset=utf-8";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            case "webp":
                return "image/webp";
            case "ico":
                return "image/x-icon";
            case "txt":
                return "text/plain; charset=utf-8";
            default:
                return "application/octet-stream";
        }
    }
    
    /**
     * Formate la taille d'un fichier
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
