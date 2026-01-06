package com.retroplay.usecases;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import com.retroplay.Game;
import com.retroplay.ConsoleConfigActivity;
import com.retroplay.RetroArchEmulatorActivity;
import com.retroplay.WebViewActivity;
import com.retroplay.FullscreenCustomTabsActivity;
import com.retroplay.helpers.RomPathResolver;
// ConsoleNameMapper n'est pas utilisé dans LaunchGameUseCase (utilisé seulement dans extractToCacheAsync)
import com.retroplay.CoreOverrideManager;
import com.retroplay.CoreOverride;
import java.io.File;

/**
 * Use Case pour le lancement des jeux (WASM, RetroArch, Custom Tabs).
 * Extracted from GameDetailsActivity to improve modularity and testability.
 * 
 * Gère:
 * - Lancement WASM (WebView ou Custom Tabs)
 * - Lancement RetroArch natif
 * - Extraction de ROMs vers cache si nécessaire
 * - Détection de formats de ROM
 * - Gestion des core overrides
 */
public class LaunchGameUseCase {

    private static final String TAG = "LaunchGameUseCase";
    private static final long MIN_NATIVE_LAUNCH_INTERVAL_MS = 1000; // Minimum 1 seconde entre deux lancements natifs

    private final Context context;
    private long lastNativeLaunchTime = 0;

    /**
     * Interface pour les callbacks UI
     */
    public interface LaunchCallbacks {
        void showToast(String message, int duration);

        void showProgressDialog(String message);

        void dismissProgressDialog();

        void runOnUiThread(Runnable action);

        void startActivity(Intent intent);

        File getCacheDir();

        String getRealConsoleDirectory(String consoleName);

        String getCurrentGalleryGameId();

        String getCurrentPsxSerial();
    }

    private final LaunchCallbacks callbacks;

    public LaunchGameUseCase(Context context, LaunchCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }

    /**
     * Lance un jeu en mode WASM (WebView ou Custom Tabs)
     */
    public void launchWasm(Game game) {
        Log.i(TAG, "Lancement du jeu (WASM): " + game.getName());

        // Get console configuration
        ConsoleConfigActivity.ConsoleConfig config = ConsoleConfigActivity.getConfig(context, game.getConsole());

        // Get core override if exists
        String fileName = game.getPath();
        if (fileName.startsWith("./")) {
            fileName = fileName.substring(2);
        }
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        String consoleDir = callbacks.getRealConsoleDirectory(game.getConsole());
        String relativePath = consoleDir + "/" + fileName;

        CoreOverrideManager manager = CoreOverrideManager.getInstance();
        String coreOverride = null;
        if (manager.hasOverride(relativePath)) {
            CoreOverride override = manager.getOverride(relativePath);
            coreOverride = override.getCoreId();
            Log.i(TAG, "Using core override for EmulatorJS: " + coreOverride);
        }

        // PSP utilise Chrome Custom Tabs pour SharedArrayBuffer et multi-threading
        if (game.getConsole().equals("psp")) {
            Log.i(TAG, "Launching PSP with Chrome Custom Tabs (for threads support)");
            launchWithCustomTabsPublic(game, config, coreOverride);
        } else {
            // Autres consoles utilisent WebView
            Log.i(TAG, "Launching with WebView");
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("file", game.getFileUrl());
            intent.putExtra("gameName", game.getName());
            intent.putExtra("console", game.getConsole());
            intent.putExtra("touchScale", config.touchScale);
            intent.putExtra("touchAlpha", config.touchAlpha);
            if (coreOverride != null) {
                intent.putExtra("core", coreOverride);
            }
            if (config.useDpad && (game.getConsole().equals("psx") || game.getConsole().equals("ps1")
                    || game.getConsole().equals("playstation"))) {
                intent.putExtra("useDpad", true);
            }
            callbacks.startActivity(intent);
        }
    }

    /**
     * Lance un jeu en mode RetroArch natif
     */
    public void launchRetroArch(Game game, int slot) {
        Log.i(TAG, "🚀 launchRetroArch() CALLED - Game: " + game.getName() + ", Slot: " + slot);

        // Vérifier le délai minimum entre deux lancements natifs
        long currentTime = System.currentTimeMillis();
        long timeSinceLastLaunch = currentTime - lastNativeLaunchTime;

        if (timeSinceLastLaunch < MIN_NATIVE_LAUNCH_INTERVAL_MS) {
            long remainingDelay = MIN_NATIVE_LAUNCH_INTERVAL_MS - timeSinceLastLaunch;
            Log.w(TAG, "⏳ Too fast! Waiting " + remainingDelay + "ms before launching (core cleanup)");

            callbacks.showToast("Please wait...", Toast.LENGTH_SHORT);

            callbacks.runOnUiThread(() -> {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    launchRetroArch(game, slot);
                }, remainingDelay);
            });
            return;
        }

        lastNativeLaunchTime = currentTime;

        String slotInfo = (slot == 0) ? "[NEW GAME]" : "[LOAD SLOT " + slot + "]";
        Log.i(TAG, "✅ Lancement du jeu (RETROPLAY): " + game.getName() + " " + slotInfo);

        // Utiliser RomPathResolver pour gérer correctement les caractères spéciaux
        String romPath = RomPathResolver.resolveRomPath(game);
        Log.i(TAG, "Resolved ROM path (handles special chars): " + romPath);

        // Vérifier que le fichier existe
        File romFile = new File(romPath);
        if (!romFile.exists()) {
            Log.e(TAG, "ROM file does not exist: " + romPath);
            callbacks.showToast("Error: ROM file not found: " + romPath, Toast.LENGTH_LONG);
            return;
        }

        // Extraire le nom du fichier
        String fileName = romPath;
        int lastSlash = romPath.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = romPath.substring(lastSlash + 1);
        }
        Log.i(TAG, "ROM filename: " + fileName);

        // DETECTION INTELLIGENTE DES FORMATS
        String console = game.getConsole().toLowerCase();

        // Formats compressés natifs supportés par les cores
        boolean isNativeCompressedFormat = fileName.endsWith(".pbp") || // PSX PSP format
                fileName.endsWith(".chd") || // Compressed Hunks of Data
                fileName.endsWith(".cso") || // PSP compressed ISO
                fileName.endsWith(".daa"); // PowerISO compressed

        // Exception: FBNeo/Arcade/MAME ROMs en .zip ne doivent PAS être extraites
        boolean isArcadeZip = (console.toLowerCase().startsWith("fbneo") ||
                console.equals("arcade") ||
                console.equals("mame") ||
                console.equals("neogeo")) &&
                fileName.endsWith(".zip");

        // Archives nécessitant extraction (.zip, .7z)
        boolean isArchive = (fileName.endsWith(".zip") || fileName.endsWith(".7z")) && !isArcadeZip;

        if (isNativeCompressedFormat) {
            Log.i(TAG, console + ": Native compressed format detected, loading directly: " + fileName);
            // Charger directement sans extraction
            launchWithRomPath(game, romPath, slot);
        } else if (isArcadeZip) {
            Log.i(TAG, console + ": Arcade ROM .zip detected, loading directly (core reads .zip natively)");
            // Charger directement
            launchWithRomPath(game, romPath, slot);
        } else if (isArchive) {
            // Extraire vers le cache
            Log.i(TAG, console + ": Archive detected (" + fileName + "), extracting to cache...");
            // Note: L'extraction sera gérée par l'Activity via extractToCacheAsync
            // Ici on retourne juste une indication qu'une extraction est nécessaire
            callbacks.showToast("Archive detected, extraction needed", Toast.LENGTH_SHORT);
            // L'Activity devra appeler extractToCacheAsync puis relancer
        } else {
            // ROM directe, lancer immédiatement
            launchWithRomPath(game, romPath, slot);
        }
    }

    /**
     * Lance avec un chemin ROM direct (après extraction ou si format natif)
     */
    private void launchWithRomPath(Game game, String romPath, int slot) {
        // PSX Serial Support
        if (game.getConsole().equals("psx") || game.getConsole().equals("ps1")
                || game.getConsole().equals("playstation")) {
            String serial = com.retroplay.util.PsxSerialExtractor.INSTANCE.extractSerial(romPath);
            if (serial != null) {
                Log.i(TAG, "[PSX] Serial found: " + serial);
            }
        }

        // Calculate CRC32 and lookup game metadata
        String gameCRC = com.retroplay.database.DatabaseManager.INSTANCE.calculateCRC32(romPath);

        // Fallback: Si le calcul échoue, utiliser le hash du gamelist.json si c'est un
        // CRC32
        if (gameCRC == null && game.getHash() != null && !game.getHash().isEmpty()) {
            String hash = game.getHash();
            if (hash.length() == 8 && hash.matches("[0-9A-Fa-f]{8}")) {
                gameCRC = hash.toUpperCase();
                Log.i(TAG, "Using CRC32 from gamelist.json: " + gameCRC);
            }
        }

        if (gameCRC != null) {
            Log.i(TAG, "ROM CRC32: " + gameCRC);

            com.retroplay.database.GameInfo gameInfo = com.retroplay.database.DatabaseManager.INSTANCE
                    .lookupGame(gameCRC, game.getConsole());
            if (gameInfo != null) {
                Log.i(TAG, "✅ Game identified from database:");
                Log.i(TAG, "  Name: " + gameInfo.getName());
                Log.i(TAG, "  Genre: " + gameInfo.getGenre());
                Log.i(TAG, "  Developer: " + gameInfo.getDeveloper());
                Log.i(TAG, "  Year: " + gameInfo.getReleaseYear());
                Log.i(TAG, "  " + gameInfo.getDisplayInfo());

                // Check cheats available
                File cheatFile = com.retroplay.database.DatabaseManager.INSTANCE.getCheatsPath(gameInfo,
                        game.getConsole());
                if (cheatFile != null && cheatFile.exists()) {
                    int cheatCount = countCheatsInFile(cheatFile);
                    Log.i(TAG, "  🎮 " + cheatCount + " cheats available!");

                    callbacks.showToast(
                            cheatCount + " cheats available • " + gameInfo.getDisplayInfo(),
                            Toast.LENGTH_LONG);
                }
            } else {
                Log.w(TAG, "⚠️ Game not found in database (CRC: " + gameCRC + ")");
            }
        } else {
            Log.w(TAG, "⚠️ Could not determine CRC32 for game");
        }

        // Determine which emulator activity to use
        Class<?> emulatorActivity = RetroArchEmulatorActivity.class;

        Intent intent = new Intent(context, emulatorActivity);
        intent.putExtra("romPath", romPath);
        intent.putExtra("gameName", game.getName());
        intent.putExtra("gameId", callbacks.getCurrentGalleryGameId());
        intent.putExtra("console", game.getConsole());
        intent.putExtra("loadSlot", slot);

        // Pass database metadata if available
        if (gameCRC != null) {
            intent.putExtra("gameCRC", gameCRC);
        }

        // Pass Config ID (Priority: Serial > CRC > Name)
        String configId = gameCRC;
        String psxSerial = callbacks.getCurrentPsxSerial();
        if (psxSerial != null) {
            configId = psxSerial;
            intent.putExtra("psxSerial", psxSerial);
        } else if (configId == null) {
            // Check local extraction for PSX
            if ((game.getConsole().equals("psx") || game.getConsole().equals("ps1")
                    || game.getConsole().equals("playstation"))) {
                String localSerial = com.retroplay.util.PsxSerialExtractor.INSTANCE.extractSerial(romPath);
                if (localSerial != null) {
                    configId = localSerial;
                    intent.putExtra("psxSerial", localSerial);
                }
            }

            if (configId == null) {
                String safeName = game.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
                configId = safeName;
            }
        }
        intent.putExtra("configId", configId);
        Log.i(TAG, "🚀 Launching emulator (Native) with Config ID: " + configId);
        Log.i(TAG, "📦 Intent extras: romPath=" + romPath + ", gameName=" + game.getName() + ", console="
                + game.getConsole());

        try {
            Log.i(TAG, "📞 Calling callbacks.startActivity()...");
            callbacks.startActivity(intent);
            Log.i(TAG, "✅ callbacks.startActivity() completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ ERROR calling startActivity: " + e.getMessage(), e);
            callbacks.showToast("Error launching game: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    /**
     * Lance via Chrome Custom Tabs (pour PSP avec threads) - méthode publique pour
     * compatibilité
     */
    public void launchWithCustomTabsPublic(Game game, ConsoleConfigActivity.ConsoleConfig config, String coreOverride) {
        launchWithCustomTabs(game, config, coreOverride);
    }

    /**
     * Lance via Chrome Custom Tabs (pour PSP avec threads)
     */
    private void launchWithCustomTabs(Game game, ConsoleConfigActivity.ConsoleConfig config, String coreOverride) {
        try {
            String gameSlug = generateSlug(game.getName());

            // Construire l'URL avec option D-Pad si activée
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("http://localhost:7777/gamelibrary/emulator.html?slug=").append(gameSlug);
            urlBuilder.append("&console=").append(game.getConsole());

            if (config.useDpad) {
                urlBuilder.append("&dpad=true");
                Log.i(TAG, "PSP with D-Pad controls (no analog sticks)");
            } else {
                Log.i(TAG, "PSP with Analog controls (DualShock with sticks)");
            }

            // Add core override if exists
            if (coreOverride != null) {
                urlBuilder.append("&core=").append(coreOverride);
                Log.i(TAG, "Using core override for EmulatorJS: " + coreOverride);
            }

            String emulatorUrl = urlBuilder.toString();
            Log.i(TAG, "Launching fullscreen Custom Tabs: " + emulatorUrl);

            // Lancer via FullscreenCustomTabsActivity
            Intent intent = new Intent(context, FullscreenCustomTabsActivity.class);
            intent.putExtra("url", emulatorUrl);
            callbacks.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error launching Custom Tabs: " + e.getMessage());
            callbacks.showToast("Erreur lors du lancement: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    /**
     * Génère un slug à partir du nom du jeu
     */
    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("['\"`]", "")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Compte le nombre de cheats dans un fichier .cht
     */
    private int countCheatsInFile(File cheatFile) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cheatFile));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("cheats =")) {
                    reader.close();
                    return Integer.parseInt(line.split("=")[1].trim());
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Error counting cheats: " + e.getMessage());
        }
        return 0;
    }
}

