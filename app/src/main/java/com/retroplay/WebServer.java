package com.retroplay;

import android.content.Context;
import android.util.Log;
import com.retroplay.utils.WebServerUtils;
import com.retroplay.helpers.ConsoleNameHelper;
import com.retroplay.utils.FileIconHelper;
import com.retroplay.server.HtmlGenerator;
import com.retroplay.server.HttpRequestHandler;
import com.retroplay.server.GameDataRequestHandler;
import com.retroplay.server.FileRequestHandler;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

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
        try (OutputStream outputStream = clientSocket.getOutputStream()) {

            // Parser la requête HTTP via HttpRequestHandler
            HttpRequestHandler requestHandler = new HttpRequestHandler();
            HttpRequestHandler.HttpRequest request = requestHandler.parseRequest(clientSocket);

            if (request == null) {
                sendErrorResponse(outputStream, 400, "Bad Request");
                return;
            }

            // Valider la méthode HTTP
            if (!"GET".equals(request.method) && !"HEAD".equals(request.method)) {
                sendErrorResponse(outputStream, 405, "Method Not Allowed");
                return;
            }

            // Servir le fichier avec ou sans headers COEP/COOP selon l'hôte
            serveFile(outputStream, request.path, request.method, request.enableSharedArrayBuffer);

        } catch (java.net.SocketException e) {
            // "Broken pipe" est normal quand le client ferme la connexion
            if (e.getMessage() == null || !e.getMessage().contains("Broken pipe")) {
                Log.e(TAG, "Erreur socket traitement client", e);
            } else {
                Log.d(TAG, "Client closed connection (normal)");
            }
        } catch (IOException e) {
            // Autres IOExceptions
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection (normal)");
            } else {
                Log.e(TAG, "Erreur traitement client", e);
            }
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
    private void serveFile(OutputStream outputStream, String path, String method, boolean enableSharedArrayBuffer)
            throws IOException {
        try {
            // Nettoyer et décoder le chemin via HttpRequestHandler
            HttpRequestHandler requestHandler = new HttpRequestHandler();
            String cleanPath = requestHandler.cleanPath(path);

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
                GameDataRequestHandler gameDataHandler = new GameDataRequestHandler();
                gameDataHandler.serveGameDataFile(outputStream, cleanPath, method, enableSharedArrayBuffer);
                return;
            }

            // Servir les fichiers statiques via FileRequestHandler
            FileRequestHandler fileHandler = new FileRequestHandler(SITES_DIR, foldersFirst, exactSize, customCSS);
            fileHandler.serveFile(outputStream, cleanPath, method);

        } catch (java.net.SocketException e) {
            // "Broken pipe" est normal quand le client ferme la connexion
            if (e.getMessage() == null || !e.getMessage().contains("Broken pipe")) {
                Log.e(TAG, "Erreur socket service fichier: " + path, e);
            } else {
                Log.d(TAG, "Client closed connection during file serve: " + path);
            }
        } catch (java.io.IOException e) {
            // Autres IOExceptions
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection during file serve: " + path);
            } else {
                Log.e(TAG, "Erreur IO service fichier: " + path, e);
                try {
                    sendErrorResponse(outputStream, 500, "Internal Server Error");
                } catch (Exception ignored) {
                    // Ignorer si le client a déjà fermé la connexion
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur service fichier: " + path, e);
            try {
                sendErrorResponse(outputStream, 500, "Internal Server Error");
            } catch (Exception ignored) {
                // Ignorer si le client a déjà fermé la connexion
            }
        }
    }

    /**
     * Sert les fichiers de l'émulateur relax depuis les assets
     */
    private void serveRelaxFile(OutputStream outputStream, String path, String method, boolean enableSharedArrayBuffer)
            throws IOException {
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

    // Méthodes serveGameDataFile(), serveAutoGeneratedGamelist(),
    // serveDirectoryListing()
    // et isRomFileForAutoGen() déplacées vers GameDataRequestHandler

    /**
     * Sert les fichiers de la bibliothèque de jeux depuis les assets
     */
    private void serveGameLibraryFile(OutputStream outputStream, String path, String method,
            boolean enableSharedArrayBuffer) throws IOException {
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

            // Gestion spéciale pour gamelist.json - servir depuis les assets (ancien
            // système)
            if (assetPath.equals("gamelist.json")) {
                serveGamelistJson(outputStream, method);
                return;
            }

            // Pour tous les fichiers HTML et JS, essayer de charger depuis le stockage
            // d'abord
            if (assetPath.endsWith(".html") || assetPath.endsWith(".js")) {
                if (tryServeFromStorage(outputStream, assetPath, method, enableSharedArrayBuffer)) {
                    return; // Fichier servi depuis le stockage
                }
                // Sinon, continuer pour servir depuis les assets
            }

            // Construire le chemin complet vers l'asset
            String fullAssetPath = "sites/gamelibrary/" + assetPath;

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
            // Gérer les "Broken pipe" silencieusement
            if (e instanceof java.net.SocketException &&
                    e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection during game library file serve: " + path);
            } else if (e instanceof java.io.IOException &&
                    e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection during game library file serve: " + path);
            } else {
                Log.e(TAG, "Erreur lors du service du fichier game library: " + path, e);
                try {
                    sendErrorResponse(outputStream, 404, "Not Found");
                } catch (Exception ignored) {
                    // Ignorer si le client a déjà fermé la connexion
                }
            }
        }
    }

    /**
     * Verifie si un repertoire est un repertoire de console valide
     * Accepte les repertoires avec gamelist.json OU contenant des fichiers ROM
     */
    private boolean isConsoleDirectory(String dirName) {
        // Ignorer les repertoires systeme
        if (dirName.equals("data") || dirName.equals("emulatorjs") || dirName.equals("vmnes")
                || dirName.equals("playlists")) {
            return false;
        }

        // Verifier si le repertoire existe dans GameLibrary-Data/roms
        java.io.File consoleDir = new java.io.File("/storage/emulated/0/GameLibrary-Data/roms/" + dirName);
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
     * Scanne récursivement les sous-répertoires (max 2 niveaux pour performance)
     */
    private boolean hasRomFiles(java.io.File dir) {
        return hasRomFiles(dir, 0, 2); // Max 2 niveaux de profondeur
    }

    /**
     * Vérifie si un répertoire contient des fichiers ROM (récursif)
     */
    private boolean hasRomFiles(java.io.File dir, int depth, int maxDepth) {
        if (!dir.exists() || !dir.isDirectory() || depth > maxDepth) {
            return false;
        }

        String[] romExtensions = {
                // Nintendo
                ".nes", ".fds", ".unf", // NES/Famicom
                ".smc", ".sfc", ".fig", // SNES
                ".n64", ".z64", ".v64", // N64
                ".gb", ".gbc", // Game Boy / Color
                ".gba", ".agb", // Game Boy Advance
                ".nds", ".dsi", // Nintendo DS
                ".3ds", ".3dsx", ".cia", // Nintendo 3DS

                // Sega
                ".md", ".gen", ".smd", ".32x", // Genesis/Mega Drive
                ".gg", // Game Gear
                ".sms", // Master System
                ".sat", // Saturn

                // Sony
                ".iso", ".cue", ".bin", ".img", ".mdf", ".chd", // PS1/PS2/etc + CHD
                ".pbp", ".cso", // PSP

                // Atari
                ".a26", ".a52", ".a78", // Atari 2600/5200/7800
                ".st", ".stx", // Atari ST
                ".xex", ".atr", ".bin", // Atari 8-bit
                ".lnx", // Atari Lynx

                // Other
                ".pce", ".sgx", // PC Engine / TurboGrafx-16
                ".ngp", ".ngc", // Neo Geo Pocket
                ".ws", ".wsc", // WonderSwan
                ".vec", // Vectrex
                ".int", // Intellivision
                ".col", // ColecoVision
                ".min", // Pokemon Mini
                ".vb", // Virtual Boy
                ".d64", ".d71", ".d81", ".t64", ".tap", ".prg", ".crt", // Commodore 64
                ".dsk", ".adf", ".ipf", // Amiga / Amstrad

                // Archives
                ".zip", ".7z", ".rar", ".gz", // Compressed files
                ".rom" // Generic ROM
        };

        java.io.File[] files = dir.listFiles();
        if (files == null)
            return false;

        for (java.io.File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                for (String ext : romExtensions) {
                    if (fileName.endsWith(ext)) {
                        return true;
                    }
                }
            } else if (file.isDirectory() && depth < maxDepth) {
                // Scanner récursivement les sous-répertoires (ignorer les répertoires système)
                String dirName = file.getName().toLowerCase();
                if (!dirName.equals("media") && !dirName.equals("saves") &&
                        !dirName.equals("states") && !dirName.equals("cheats") &&
                        !dirName.equals("overlays") && !dirName.equals("cores") &&
                        !dirName.equals("bios") && !dirName.startsWith(".")) {
                    if (hasRomFiles(file, depth + 1, maxDepth)) {
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
    private boolean tryServeFromStorage(OutputStream outputStream, String fileName, String method,
            boolean enableSharedArrayBuffer) {
        try {
            // Chemin vers le fichier personnalisé dans le stockage
            // (RetroPlay-Files/sites/gamelibrary/)
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

                // HEAD request - juste envoyer le header avec headers SharedArrayBuffer
                // conditionnels
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

    private void serveConsoleFile(OutputStream outputStream, String assetPath, String method,
            boolean enableSharedArrayBuffer) throws IOException {
        try {
            // Déterminer la console et le nom du fichier
            // assetPath est du format: "nes/file.zip" ou "snes/gamelist.json" ou
            // "n64/file.z64"
            String console = assetPath.substring(0, assetPath.indexOf('/'));
            String fileName = assetPath.substring(assetPath.indexOf('/') + 1);

            // Cas spécial: gamelist.json n'existe pas → Générer automatiquement!
            if (fileName.equals("gamelist.json")) {
                String filePath = "/storage/emulated/0/GameLibrary-Data/roms/" + console + "/gamelist.json";
                java.io.File file = new java.io.File(filePath);

                if (!file.exists()) {
                    Log.i(TAG, "gamelist.json not found, generating automatically from ROMs for: " + console);
                    // Utiliser GameDataRequestHandler pour générer le gamelist
                    GameDataRequestHandler gameDataHandler = new GameDataRequestHandler();
                    gameDataHandler.serveGameDataFile(outputStream, "/gamedata/" + console + "/gamelist.json", method,
                            enableSharedArrayBuffer);
                    return;
                }
            }

            // Construire le chemin complet vers le fichier dans GameLibrary-Data/roms
            String filePath = "/storage/emulated/0/GameLibrary-Data/roms/" + console + "/" + fileName;

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
            html.append(
                    "body { font-family: 'Courier New', monospace; background: #000; color: #ff3333; min-height: 100vh; padding: 20px; }\n");
            html.append(".header { text-align: center; margin-bottom: 40px; }\n");
            html.append(
                    "h1 { font-size: 2.5em; color: #ff3333; text-shadow: 2px 2px 4px rgba(0,0,0,0.8); margin-bottom: 10px; }\n");
            html.append(".subtitle { font-size: 1.1em; color: #ff6666; }\n");
            html.append(
                    ".consoles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; max-width: 1200px; margin: 0 auto; }\n");
            html.append(
                    ".console-card { background: #330000; border-radius: 12px; padding: 20px; border: 2px solid #ff3333; transition: all 0.3s ease; cursor: pointer; text-decoration: none; color: inherit; display: block; }\n");
            html.append(
                    ".console-card:hover { transform: translateY(-5px); box-shadow: 0 10px 30px rgba(255, 51, 51, 0.5); border-color: #ff6666; background: #660000; }\n");
            html.append(".console-icon { font-size: 3em; text-align: center; margin-bottom: 15px; }\n");
            html.append(
                    ".console-name { font-size: 1.5em; font-weight: bold; text-align: center; margin-bottom: 10px; }\n");
            html.append(
                    ".console-info { font-size: 0.9em; color: #ff6666; text-align: center; margin-bottom: 5px; }\n");
            html.append(
                    ".badge { display: inline-block; background: #ff6666; color: #000; padding: 4px 8px; border-radius: 4px; font-size: 0.7em; font-weight: bold; margin-top: 10px; }\n");
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
                            String fullName = ConsoleNameHelper.getConsoleFullName(dirName);
                            String color = ConsoleNameHelper.getConsoleColor(dirName);
                            String defaultCore = ConsoleNameHelper.getDefaultCore(dirName);

                            html.append("<a href=\"./index.html?console=").append(dirName)
                                    .append("\" class=\"console-card\" style=\"border-color: ").append(color)
                                    .append(";\">\n");
                            html.append("<div class=\"console-icon\">🎮</div>\n");
                            html.append("<div class=\"console-name\" style=\"color: ").append(color).append(";\">")
                                    .append(dirName.toUpperCase()).append("</div>\n");
                            html.append("<div class=\"console-info\">").append(fullName).append("</div>\n");
                            html.append("<div class=\"console-info\">Core: ").append(defaultCore).append("</div>\n");

                            // Badge selon le mode
                            if (!hasGamelist) {
                                html.append(
                                        "<div style=\"text-align: center;\"><span class=\"badge\" style=\"background: #00ff00; color: #000;\">AUTO SCAN</span></div>\n");
                            } else if (!hasPresetConfig(dirName)) {
                                html.append(
                                        "<div style=\"text-align: center;\"><span class=\"badge\">AUTO-DETECTED</span></div>\n");
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
                        if (dirName.equals("data") || dirName.equals("emulatorjs") || dirName.equals("vmnes")
                                || dirName.equals("playlists") ||
                                dirName.equals("saves") || dirName.equals("states") || dirName.equals("cheats") ||
                                dirName.equals("media") || dirName.equals("overlays") || dirName.equals("cores") ||
                                dirName.equals("bios") || dirName.startsWith(".")) {
                            continue;
                        }

                        // Normaliser le nom du répertoire avec ConsoleNameMapper pour gérer les noms
                        // alternatifs
                        String normalizedDirName = ConsoleNameMapper.normalizeToCanonical(dirName);

                        // Vérifier si gamelist.json existe OU si le répertoire contient des ROMs
                        java.io.File gamelistFile = new java.io.File(dir, "gamelist.json");
                        boolean hasGamelist = gamelistFile.exists();
                        boolean hasRoms = hasRomFiles(dir);

                        if (hasGamelist || hasRoms) {
                            // Utiliser le nom normalisé pour la détection, mais garder le nom original du
                            // répertoire
                            org.json.JSONObject consoleInfo = detectConsoleConfig(dir, normalizedDirName);

                            if (consoleInfo != null) {
                                // S'assurer que le nom du répertoire original est conservé
                                consoleInfo.put("directory", dirName);
                                consoleInfo.put("gamelistPath", dirName + "/gamelist.json");

                                // Ajouter un flag pour indiquer le mode AUTO SCAN
                                consoleInfo.put("autoScan", !hasGamelist);
                                consolesArray.put(consoleInfo);
                                String mode = hasGamelist ? "gamelist.json" : "AUTO SCAN";
                                Log.d(TAG,
                                        "Console detected: " + dirName + " (normalized: " + normalizedDirName + ") - " +
                                                consoleInfo.optString("fullName") + " - Mode: " + mode);
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

        } catch (java.net.SocketException e) {
            // "Broken pipe" est normal quand le client ferme la connexion avant la fin de
            // l'envoi
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection during consoles API response (normal)");
            } else {
                Log.e(TAG, "Erreur socket lors du service de l'API consoles", e);
            }
        } catch (java.io.IOException e) {
            // Autres IOExceptions (peuvent aussi être des "Broken pipe")
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                Log.d(TAG, "Client closed connection during consoles API response (normal)");
            } else {
                Log.e(TAG, "Erreur IO lors du service de l'API consoles", e);
                try {
                    sendErrorResponse(outputStream, 500, "Internal Server Error");
                } catch (Exception ignored) {
                    // Ignorer si le client a déjà fermé la connexion
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du service de l'API consoles", e);
            try {
                sendErrorResponse(outputStream, 500, "Internal Server Error");
            } catch (Exception ignored) {
                // Ignorer si le client a déjà fermé la connexion
            }
        }
    }

    /**
     * Detecte la configuration d'une console selon la strategie hybride:
     * 1. Lire console.json si present
     * 2. Normaliser le nom avec ConsoleNameMapper
     * 3. Utiliser les presets si disponibles
     * 4. Auto-detecter depuis cores.json
     * 5. Fallback generique
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
                    // Si l'ID est spécifié dans console.json, l'utiliser, sinon normaliser dirName
                    String canonicalId = ConsoleNameMapper.normalizeToCanonical(
                            userConfig.optString("id", dirName));
                    consoleInfo.put("id", canonicalId);
                    consoleInfo.put("name", userConfig.optString("name", canonicalId.toUpperCase()));
                    consoleInfo.put("fullName", userConfig.optString("fullName",
                            ConsoleNameMapper.getFullName(canonicalId) != null
                                    ? ConsoleNameMapper.getFullName(canonicalId)
                                    : canonicalId.toUpperCase()));
                    consoleInfo.put("directory", dirName); // Garder le nom original du répertoire
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

            // 2. Normaliser le nom du répertoire vers ID canonique
            String canonicalId = ConsoleNameMapper.normalizeToCanonical(dirName);
            Log.d(TAG, "Normalized directory name '" + dirName + "' -> canonical ID '" + canonicalId + "'");

            // 3. Utiliser les presets si disponibles (avec ID canonique)
            if (hasPresetConfig(canonicalId)) {
                consoleInfo.put("id", canonicalId);
                consoleInfo.put("name", canonicalId.toUpperCase());
                consoleInfo.put("fullName", getConsoleFullName(canonicalId));
                consoleInfo.put("directory", dirName); // Garder le nom original du répertoire
                consoleInfo.put("gamelistPath", dirName + "/gamelist.json");
                consoleInfo.put("cores", getConsoleCores(canonicalId));
                consoleInfo.put("defaultCore", getDefaultCore(canonicalId));
                consoleInfo.put("extensions", getConsoleExtensions(canonicalId));
                consoleInfo.put("icon", "🎮");
                consoleInfo.put("color", getConsoleColor(canonicalId));
                consoleInfo.put("enabled", true);

                Log.d(TAG, "Using preset config for: " + dirName + " (canonical: " + canonicalId + ")");
                return consoleInfo;
            }

            // 4. Auto-detecter depuis cores.json (avec ID canonique)
            org.json.JSONObject autoDetectedConfig = autoDetectFromCoresJson(canonicalId);
            if (autoDetectedConfig != null) {
                // Mettre à jour l'ID canonique et garder le nom du répertoire
                autoDetectedConfig.put("id", canonicalId);
                autoDetectedConfig.put("directory", dirName);
                autoDetectedConfig.put("gamelistPath", dirName + "/gamelist.json");
                Log.d(TAG,
                        "Auto-detected config from cores.json for: " + dirName + " (canonical: " + canonicalId + ")");
                return autoDetectedConfig;
            }

            // 5. Fallback generique
            Log.d(TAG, "Using generic fallback config for: " + dirName);
            consoleInfo.put("id", canonicalId);
            consoleInfo.put("name", canonicalId.toUpperCase());
            String fullName = ConsoleNameMapper.getFullName(canonicalId);
            consoleInfo.put("fullName", fullName != null ? fullName : (canonicalId.toUpperCase() + " Console"));
            consoleInfo.put("directory", dirName); // Garder le nom original du répertoire
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
        // Normaliser d'abord avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleId);

        switch (canonicalId) {
            // Nintendo
            case "nes":
            case "snes":
            case "n64":
            case "gb":
            case "gbc":
            case "gba":
            case "nds":
            case "ds":
                // Sega
            case "genesis":
            case "mastersystem":
            case "gamegear":
            case "32x":
            case "segacd":
            case "saturn":

                // Sony
            case "psx":
            case "psp":
                // Atari
            case "atari2600":
            case "atari5200":
            case "atari7800":
            case "lynx":
            case "jaguar":
                // Autres
            case "3do":
            case "arcade":
            case "mame":
            case "fbneo":
            case "ngp":
            case "wonderswancolor":
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

    // Fonction getConsoleFullName déplacée vers ConsoleNameHelper
    private String getConsoleFullName(String consoleId) {
        return ConsoleNameHelper.getConsoleFullName(consoleId);
    }

    private org.json.JSONArray getConsoleCores(String consoleId) throws org.json.JSONException {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleId);

        org.json.JSONArray cores = new org.json.JSONArray();
        switch (canonicalId) {
            // Nintendo
            case "nes":
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

    // Fonction getDefaultCore déplacée vers ConsoleNameHelper
    private String getDefaultCore(String consoleId) {
        return ConsoleNameHelper.getDefaultCore(consoleId);
    }

    private org.json.JSONArray getConsoleExtensions(String consoleId) throws org.json.JSONException {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleId);

        org.json.JSONArray extensions = new org.json.JSONArray();
        switch (canonicalId) {
            // Nintendo
            case "nes":
                extensions.put(".nes").put(".fds").put(".unif").put(".unf").put(".zip").put(".rar");
                break;
            case "snes":
                extensions.put(".smc").put(".sfc").put(".swc").put(".fig").put(".bs").put(".st").put(".zip")
                        .put(".rar");
                break;
            case "n64":
                extensions.put(".n64").put(".v64").put(".z64").put(".bin").put(".u1").put(".ndd").put(".zip")
                        .put(".rar");
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
                extensions.put(".md").put(".gen").put(".smd").put(".bin").put(".68k").put(".sgd").put(".zip")
                        .put(".rar");
                break;
            case "mastersystem":
                extensions.put(".sms").put(".zip").put(".rar");
                break;
            case "gamegear":
                extensions.put(".gg").put(".zip").put(".rar");
                break;
            case "32x":
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
                extensions.put(".cue").put(".toc").put(".m3u").put(".ccd").put(".exe").put(".pbp").put(".chd")
                        .put(".zip");
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
                extensions.put(".a78").put(".bin").put(".zip").put(".7z");
                break;
            case "lynx":
                extensions.put(".lnx").put(".zip");
                break;
            case "jaguar":
                extensions.put(".j64").put(".jag").put(".rom").put(".abs").put(".cof").put(".bin").put(".prg")
                        .put(".zip");
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
                extensions.put(".pce").put(".cue").put(".ccd").put(".iso").put(".img").put(".bin").put(".chd")
                        .put(".zip");
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
                extensions.put(".adf").put(".adz").put(".dms").put(".fdi").put(".ipf").put(".hdf").put(".hdz")
                        .put(".lha").put(".zip").put(".7z");
                break;
            case "c64":
            case "commodore64":
                extensions.put(".d64").put(".d71").put(".d81").put(".t64").put(".tap").put(".prg").put(".crt")
                        .put(".bin").put(".zip");
                break;

            default:
                extensions.put(".zip").put(".rar").put(".7z");
                break;
        }
        return extensions;
    }

    // Fonction getConsoleColor déplacée vers ConsoleNameHelper
    private String getConsoleColor(String consoleId) {
        return ConsoleNameHelper.getConsoleColor(consoleId);
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
     * Sert un directory listing pour les répertoires unified/ et games/ de
     * gamelibrary
     */
    private void serveGameLibraryDirectory(OutputStream outputStream, String directoryPath) throws IOException {
        try {
            // Lister les fichiers dans le répertoire assets/gamelibrary/
            String[] files = context.getAssets().list("gamelibrary/" + directoryPath);

            if (files == null || files.length == 0) {
                sendErrorResponse(outputStream, 404, "Directory not found");
                return;
            }

            // Générer le HTML du directory listing via HtmlGenerator
            String htmlContent = HtmlGenerator.generateGameLibraryDirectoryListing(directoryPath, files);

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: " + htmlContent.getBytes("UTF-8").length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "\r\n";

            outputStream.write(response.getBytes("UTF-8"));
            outputStream.write(htmlContent.getBytes("UTF-8"));
            outputStream.flush();

            Log.d(TAG, "Served game library directory listing: " + directoryPath);

        } catch (IOException e) {
            Log.e(TAG, "Erreur lors du listing du répertoire game library: " + directoryPath, e);
            sendErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }

    // Fonctions getFileIcon et getFileType déplacées vers FileIconHelper
    private String getFileIcon(String fileName) {
        return FileIconHelper.getFileIcon(fileName);
    }

    private String getFileType(String fileName) {
        return FileIconHelper.getFileType(fileName);
    }

    /**
     * Détermine le type de contenu basé sur l'extension du fichier
     */
    // Fonction getContentType déplacée vers WebServerUtils
    private String getContentType(String filename) {
        return WebServerUtils.getContentType(filename);
    }

    /**
     * Ajoute les headers CORS et SharedArrayBuffer requis pour PPSSPP
     */
    private void addCorsAndSharedArrayBufferHeaders(StringBuilder headers) {
        // CORS headers only (COEP/COOP removed - they block cross-origin resources and
        // don't work in WebView)
        headers.append("Access-Control-Allow-Origin: *\r\n");
    }

    // Méthodes serveStaticFile() et serveDirectory() déplacées vers
    // FileRequestHandler

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
                "<p>Pour activer SharedArrayBuffer (requis pour PSP), redirection vers <a href=\"" + location
                + "\">localhost</a></p>" +
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

    /**
     * Détermine le type MIME d'un fichier
     */
    // Fonctions getMimeType et formatFileSize déplacées vers WebServerUtils
    private String getMimeType(String fileName) {
        return WebServerUtils.getMimeType(fileName);
    }

    private String formatFileSize(long bytes) {
        return WebServerUtils.formatFileSize(bytes);
    }

    /**
     * Auto-détecte la configuration d'une console depuis cores.json
     * Cherche un core compatible avec le nom de la console
     * 
     * @param consoleDirName Nom du répertoire de la console (ex: "nes", "snes")
     * @return JSONObject avec la config auto-détectée, ou null si aucun core
     *         compatible
     */
    private org.json.JSONObject autoDetectFromCoresJson(String consoleDirName) {
        try {
            // Chemin vers cores.json
            java.io.File coresFile = new java.io.File("/storage/emulated/0/GameLibrary-Data/data/cores/cores.json");
            if (!coresFile.exists()) {
                Log.d(TAG, "cores.json not found, skipping auto-detection");
                return null;
            }

            // Lire le fichier
            java.io.FileInputStream fis = new java.io.FileInputStream(coresFile);
            byte[] buffer = new byte[(int) coresFile.length()];
            fis.read(buffer);
            fis.close();
            String jsonContent = new String(buffer, "UTF-8");

            // Parser le JSON
            org.json.JSONArray coresArray = new org.json.JSONArray(jsonContent);

            // Normaliser le nom de la console pour la recherche
            String consoleKey = consoleDirName.toLowerCase().replace("_", "").replace("-", "");

            // Mapping console → patterns de noms de cores
            java.util.List<String> corePatterns = getCorePatternsForConsole(consoleKey);

            // Chercher un core compatible
            org.json.JSONArray compatibleCores = new org.json.JSONArray();
            String defaultCoreId = null;
            String defaultCoreName = null;

            for (int i = 0; i < coresArray.length(); i++) {
                org.json.JSONObject core = coresArray.getJSONObject(i);
                String coreName = core.optString("name", "").toLowerCase();
                String coreDisplayName = core.optString("display_name", core.optString("name", ""));

                // Vérifier si le core correspond à un pattern
                for (String pattern : corePatterns) {
                    if (coreName.contains(pattern.toLowerCase())) {
                        String coreId = core.optString("id", coreName);
                        compatibleCores.put(coreId);

                        // Prendre le premier comme défaut
                        if (defaultCoreId == null) {
                            defaultCoreId = coreId;
                            defaultCoreName = coreDisplayName;
                        }
                        break;
                    }
                }
            }

            // Si aucun core compatible trouvé, retourner null
            if (compatibleCores.length() == 0) {
                Log.d(TAG, "No compatible core found in cores.json for: " + consoleDirName);
                return null;
            }

            // Construire la config auto-détectée
            org.json.JSONObject consoleInfo = new org.json.JSONObject();
            consoleInfo.put("id", consoleDirName);
            consoleInfo.put("name", consoleDirName.toUpperCase());
            consoleInfo.put("fullName", consoleDirName.toUpperCase() + " Console (Auto-detected)");
            consoleInfo.put("directory", consoleDirName);
            consoleInfo.put("gamelistPath", consoleDirName + "/gamelist.json");
            consoleInfo.put("cores", compatibleCores);
            consoleInfo.put("defaultCore", defaultCoreId != null ? defaultCoreId : compatibleCores.getString(0));

            // Extensions génériques (peut être amélioré avec un mapping)
            org.json.JSONArray genericExts = new org.json.JSONArray();
            genericExts.put(".zip").put(".rar").put(".7z");
            consoleInfo.put("extensions", genericExts);

            consoleInfo.put("icon", "🎮");
            consoleInfo.put("color", "#FF3333");
            consoleInfo.put("enabled", true);
            consoleInfo.put("isAutoDetected", true); // Marquer comme auto-détecté

            Log.i(TAG,
                    "Auto-detected core for " + consoleDirName + ": " + defaultCoreName + " (" + defaultCoreId + ")");
            return consoleInfo;

        } catch (Exception e) {
            Log.w(TAG, "Error auto-detecting from cores.json for " + consoleDirName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Retourne les patterns de noms de cores à chercher pour une console
     */
    private java.util.List<String> getCorePatternsForConsole(String consoleKey) {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleKey);

        java.util.List<String> patterns = new java.util.ArrayList<>();

        // Mapping console → patterns de cores (utilise ID canonique)
        switch (canonicalId) {
            case "nes":
                patterns.add("fceumm");
                patterns.add("fceux");
                patterns.add("mesen");
                patterns.add("nestopia");
                break;
            case "snes":
                patterns.add("snes9x");
                patterns.add("bsnes");
                patterns.add("higan");
                break;
            case "n64":
                patterns.add("parallel");
                patterns.add("mupen64");
                patterns.add("n64");
                break;
            case "gb":
            case "gbc":
                patterns.add("gambatte");
                patterns.add("sameboy");
                patterns.add("gameboy");
                break;
            case "gba":
                patterns.add("mgba");
                patterns.add("vba");
                patterns.add("gba");
                break;
            case "psx":
                patterns.add("pcsx");
                patterns.add("mednafen_psx");
                patterns.add("beetle_psx");
                break;
            case "psp":
                patterns.add("ppsspp");
                patterns.add("psp");
                break;
            case "genesis":
                patterns.add("genesis");
                patterns.add("picodrive");
                break;
            case "c64":
            case "commodore64":
                patterns.add("vice");
                patterns.add("vice_x64");
                patterns.add("vice_x64sc");
                patterns.add("vice_x128");
                break;
            case "amiga":
                patterns.add("puae");
                patterns.add("amiga");
                break;
            case "dos":
                patterns.add("dosbox");
                patterns.add("dosbox_pure");
                break;
            case "segacd":
                patterns.add("genesis");
                patterns.add("picodrive");
                patterns.add("segacd");
                break;
            case "mastersystem":
                patterns.add("genesis");
                patterns.add("mastersystem");
                break;
            case "gamegear":
                patterns.add("genesis");
                patterns.add("gamegear");
                break;
            case "32x":
                patterns.add("picodrive");
                patterns.add("32x");
                break;
            case "atari2600":
                patterns.add("stella");
                patterns.add("atari2600");
                break;
            case "atari5200":
                patterns.add("a5200");
                patterns.add("atari5200");
                break;
            case "atari7800":
                patterns.add("prosystem");
                patterns.add("atari7800");
                break;
            case "lynx":
                patterns.add("handy");
                patterns.add("lynx");
                break;
            case "ngp":
                patterns.add("mednafen_ngp");
                patterns.add("ngp");
                break;
            case "wonderswancolor":
            case "wsc":
                patterns.add("mednafen_wswan");
                patterns.add("wonderswan");
                break;
            case "pce":
            case "pcengine":
                patterns.add("mednafen_pce");
                patterns.add("pcengine");
                break;
            case "arcade":
            case "mame":
                patterns.add("mame");
                patterns.add("fbneo");
                patterns.add("fbalpha");
                break;
            case "fbneo":
            case "neogeo":
                patterns.add("fbneo");
                patterns.add("neogeo");
                break;
            case "cps1":
                patterns.add("fbalpha");
                patterns.add("cps1");
                break;
            case "cps2":
                patterns.add("fbalpha");
                patterns.add("cps2");
                break;
            default:
                // Pour les consoles inconnues, essayer de chercher le nom de la console dans le
                // nom du core
                patterns.add(consoleKey);
                break;
        }

        return patterns;
    }
}
