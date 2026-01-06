package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import com.retroplay.Game;
import com.retroplay.CoreOverrideManager;
import com.retroplay.CoreOverride;
import com.retroplay.ConsoleNameMapper;
import com.retroplay.helpers.RomPathResolver;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Use Case pour gérer les core overrides d'un jeu.
 * Extracted from GameDetailsActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement des cores disponibles pour une console
 * - Obtention du core par défaut
 * - Patterns de recherche de cores
 * - Mise à jour du texte du bouton core override
 */
public class ManageCoreOverrideUseCase {

    private static final String TAG = "ManageCoreOverrideUseCase";
    private static final String CORES_JSON_PATH = "/storage/emulated/0/GameLibrary-Data/data/cores/cores.json";

    private final Context context;

    /**
     * Interface pour obtenir le répertoire console
     */
    public interface ConsoleDirectoryProvider {
        String getRealConsoleDirectory(String consoleName);
    }

    private final ConsoleDirectoryProvider directoryProvider;

    /**
     * Classe pour représenter les informations d'un core
     */
    public static class CoreInfo {
        public final String coreId;
        public final String displayName;
        public final String fileName;
        public final String description;

        public CoreInfo(String coreId, String displayName, String fileName, String description) {
            this.coreId = coreId;
            this.displayName = displayName;
            this.fileName = fileName;
            this.description = description;
        }
    }

    /**
     * Classe pour les données du dialog
     */
    public static class CoreDialogData {
        public final List<String> coresList;
        public final List<String> coreIdsList;
        public final String defaultCoreName;
        public final int selectedPosition;
        public final String relativePath;

        public CoreDialogData(List<String> coresList, List<String> coreIdsList,
                String defaultCoreName, int selectedPosition, String relativePath) {
            this.coresList = coresList;
            this.coreIdsList = coreIdsList;
            this.defaultCoreName = defaultCoreName;
            this.selectedPosition = selectedPosition;
            this.relativePath = relativePath;
        }
    }

    public ManageCoreOverrideUseCase(Context context, ConsoleDirectoryProvider directoryProvider) {
        this.context = context;
        this.directoryProvider = directoryProvider;
    }

    /**
     * Obtient le texte à afficher sur le bouton core override
     */
    public String getCoreOverrideButtonText(Game game) {
        if (game == null) {
            return "⚙ Default";
        }

        String fileName = extractFileName(game.getPath());
        String consoleDir = directoryProvider.getRealConsoleDirectory(game.getConsole());

        // Use RomPathResolver to get the exact path structure used by the emulator
        String fullPath = RomPathResolver.resolveRomPath(game);
        String relativePath;
        if (fullPath != null && fullPath.contains("/GameLibrary-Data/")) {
            relativePath = fullPath.substring(fullPath.indexOf("/GameLibrary-Data/") + "/GameLibrary-Data/".length());
            // Strip leading slash if any
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
        } else {
            // Fallback
            relativePath = "roms/" + consoleDir + "/" + fileName;
        }

        CoreOverrideManager manager = CoreOverrideManager.getInstance();
        if (manager.hasOverride(relativePath)) {
            CoreOverride override = manager.getOverride(relativePath);
            return "⚡ " + override.getCoreId().toUpperCase();
        } else {
            // Afficher le core par défaut basé sur la console
            String defaultCore = getDefaultCoreForConsole(game.getConsole());
            return "⚙ " + defaultCore;
        }
    }

    /**
     * Prépare les données pour le dialog de sélection de core
     */
    public CoreDialogData prepareCoreDialogData(Game game) {
        if (game == null) {
            return null;
        }

        String fileName = extractFileName(game.getPath());
        String consoleDir = directoryProvider.getRealConsoleDirectory(game.getConsole());

        // Use RomPathResolver to get the exact path structure used by the emulator
        String fullPath = RomPathResolver.resolveRomPath(game);
        String relativePath;
        if (fullPath != null && fullPath.contains("/GameLibrary-Data/")) {
            relativePath = fullPath.substring(fullPath.indexOf("/GameLibrary-Data/") + "/GameLibrary-Data/".length());
            // Strip leading slash if any
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
        } else {
            // Fallback
            relativePath = "roms/" + consoleDir + "/" + fileName;
        }

        // Obtenir le core par défaut
        String defaultCoreName = getDefaultCoreForConsole(game.getConsole());

        // Charger les cores disponibles depuis cores.json
        List<CoreInfo> availableCores = loadAvailableCoresForConsole(game.getConsole());

        // Construire les listes pour le dialog
        List<String> coresList = new ArrayList<>();
        List<String> coreIdsList = new ArrayList<>();

        // Ajouter "Default" en premier
        coresList.add("Default (" + defaultCoreName + ")");
        coreIdsList.add(null);

        // Organiser les cores par catégorie
        List<CoreInfo> arcadeCores = new ArrayList<>();
        List<CoreInfo> consoleCores = new ArrayList<>();

        for (CoreInfo core : availableCores) {
            String coreId = core.coreId.toLowerCase();
            // Détecter les cores arcade
            if (coreId.contains("mame") || coreId.contains("fbneo") || coreId.contains("fbalpha") ||
                    coreId.contains("arcade")) {
                arcadeCores.add(core);
            } else {
                consoleCores.add(core);
            }
        }

        // Ajouter les cores arcade
        if (!arcadeCores.isEmpty()) {
            coresList.add("── ARCADE CORES ──");
            coreIdsList.add(null);
            for (CoreInfo core : arcadeCores) {
                coresList.add(core.displayName + (core.description.isEmpty() ? "" : " (" + core.description + ")"));
                coreIdsList.add(core.coreId);
            }
        }

        // Ajouter les cores console
        if (!consoleCores.isEmpty()) {
            coresList.add("── CONSOLE CORES ──");
            coreIdsList.add(null);
            for (CoreInfo core : consoleCores) {
                coresList.add(core.displayName + (core.description.isEmpty() ? "" : " (" + core.description + ")"));
                coreIdsList.add(core.coreId);
            }
        }

        // Si aucun core trouvé, utiliser la liste de fallback
        if (availableCores.isEmpty()) {
            Log.w(TAG, "No cores found for console: " + game.getConsole() + " in cores.json, using fallback logic");
            availableCores = getFallbackCoresForConsole(game.getConsole());

            // Re-populate lists from fallback cores
            coresList.add("── FALLBACK CORES ──");
            coreIdsList.add(null);
            for (CoreInfo core : availableCores) {
                coresList.add(core.displayName);
                coreIdsList.add(core.coreId);
            }
        }

        // Déterminer quel item est actuellement sélectionné
        CoreOverrideManager manager = CoreOverrideManager.getInstance();
        int selectedPosition = 0;
        if (manager.hasOverride(relativePath)) {
            CoreOverride override = manager.getOverride(relativePath);
            String currentCoreId = override.getCoreId();
            for (int i = 0; i < coreIdsList.size(); i++) {
                if (currentCoreId.equals(coreIdsList.get(i))) {
                    selectedPosition = i;
                    break;
                }
            }
        }

        return new CoreDialogData(coresList, coreIdsList, defaultCoreName, selectedPosition, relativePath);
    }

    /**
     * Applique un core override
     */
    public void applyCoreOverride(String relativePath, String coreId, String coreDisplayName) {
        CoreOverrideManager manager = CoreOverrideManager.getInstance();
        if (coreId == null) {
            // Default - supprimer l'override
            manager.removeOverride(relativePath);
        } else {
            // Définir un override
            String reason = "User selected: " + coreDisplayName;
            // Sanitize key (strip leading slash)
            String sanitizedPath = relativePath;
            if (sanitizedPath != null && sanitizedPath.startsWith("/")) {
                sanitizedPath = sanitizedPath.substring(1);
            }
            manager.setOverride(sanitizedPath, coreId, reason);
        }
    }

    /**
     * Extrait le nom de fichier depuis le chemin
     */
    private String extractFileName(String path) {
        if (path == null) {
            return "";
        }
        String fileName = path;
        if (fileName.startsWith("./")) {
            fileName = fileName.substring(2);
        }
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return fileName;
    }

    /**
     * Obtient le nom du core par défaut pour une console
     */
    public String getDefaultCoreForConsole(String console) {
        String consoleKey = console.toLowerCase();

        // Pour les sous-consoles, utiliser le parent
        if (consoleKey.contains("/")) {
            consoleKey = consoleKey.substring(0, consoleKey.indexOf("/"));
        }

        switch (consoleKey) {
            case "nes":
                return "FCEUmm";
            case "snes":
                return "Snes9x";
            case "n64":
                return "ParaLLEl N64";
            case "gb":
            case "gbc":
                return "Gambatte";
            case "gba":
                return "mGBA";
            case "psx":
            case "ps1":
            case "playstation":
                return "PCSX ReARMed";
            case "psp":
                return "PPSSPP";
            case "genesis":
            case "megadrive":
            case "md":
            case "scd":
            case "segacd":
            case "mastersystem":
            case "sms":
            case "gamegear":
            case "gg":
                return "Genesis Plus GX";
            case "32x":
            case "sega32x":
                return "PicoDrive";
            case "atari2600":
            case "a2600":
                return "Stella 2014";
            case "atari5200":
            case "a5200":
                return "Atari800";
            case "atari7800":
            case "a7800":
                return "ProSystem";
            case "atarilynx":
            case "lynx":
                return "Handy";
            case "ngp":
            case "ngpc":
                return "Mednafen NGP";
            case "wonderswan":
            case "wonderswancolor":
            case "ws":
            case "wsc":
                return "Mednafen WonderSwan";
            case "pce":
            case "pcengine":
                return "Mednafen PCE Fast";
            // Arcade
            case "arcade":
                return "FBNeo";
            case "mame":
                return "MAME 2010";
            case "fbneo":
                return "FBNeo";
            case "cps1":
                return "FBalpha CPS1";
            case "cps2":
                return "FBalpha CPS2";
            case "cps3":
                return "FBNeo";
            case "neogeo":
                return "FBNeo";
            default:
                return "Default";
        }
    }

    /**
     * Charge les cores disponibles pour une console depuis cores.json
     */
    public List<CoreInfo> loadAvailableCoresForConsole(String console) {
        List<CoreInfo> cores = new ArrayList<>();

        try {
            // Normaliser le nom de la console
            String consoleKey = console.toLowerCase().replace("_", "").replace("-", "");

            // Obtenir les patterns de cores pour cette console
            List<String> patterns = getCorePatternsForConsole(consoleKey);

            // Lire cores.json
            File coresFile = new File(CORES_JSON_PATH);
            if (!coresFile.exists()) {
                Log.w(TAG, "cores.json not found, using fallback cores");
                return getFallbackCoresForConsole(consoleKey);
            }

            FileInputStream fis = new FileInputStream(coresFile);
            byte[] buffer = new byte[(int) coresFile.length()];
            fis.read(buffer);
            fis.close();
            String jsonContent = new String(buffer, "UTF-8");

            // Parser le JSON
            JSONArray coresArray = new JSONArray(jsonContent);

            // Chercher les cores compatibles
            Log.d(TAG, "Searching for cores matching patterns: " + patterns.toString() + " for console: " + console);
            for (int i = 0; i < coresArray.length(); i++) {
                JSONObject core = coresArray.getJSONObject(i);
                String coreName = core.optString("name", "").toLowerCase();
                String coreDisplayName = core.optString("display_name", core.optString("name", ""));
                String coreId = core.optString("id", coreName).toLowerCase();
                String coreFileName = core.optString("file", "");

                // Normaliser les noms pour la comparaison (enlever underscores et tirets)
                String coreNameNormalized = coreName.replace("_", "").replace("-", "");
                String coreIdNormalized = coreId.replace("_", "").replace("-", "");

                // Vérifier si le core correspond à un pattern
                boolean matched = false;
                for (String pattern : patterns) {
                    String patternLower = pattern.toLowerCase();
                    String patternNormalized = patternLower.replace("_", "").replace("-", "");

                    // Vérifier dans le nom, l'ID, et les versions normalisées
                    if (coreName.contains(patternLower) || coreId.contains(patternLower) ||
                            coreNameNormalized.contains(patternNormalized)
                            || coreIdNormalized.contains(patternNormalized)) {
                        cores.add(new CoreInfo(coreId, coreDisplayName, coreFileName, ""));
                        Log.d(TAG,
                                "Matched core: " + coreDisplayName + " (id: " + coreId + ", pattern: " + pattern + ")");
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    Log.v(TAG,
                            "Core not matched: " + coreDisplayName + " (name: " + coreName + ", id: " + coreId + ")");
                }
            }

            Log.i(TAG, "Loaded " + cores.size() + " compatible cores for console: " + console);

        } catch (Exception e) {
            Log.e(TAG, "Error loading cores from cores.json", e);
            return getFallbackCoresForConsole(console.toLowerCase().replace("_", "").replace("-", ""));
        }

        return cores;
    }

    /**
     * Retourne les patterns de noms de cores à chercher pour une console
     */
    private List<String> getCorePatternsForConsole(String consoleKey) {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleKey);

        List<String> patterns = new ArrayList<>();

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
            case "ps1":
            case "playstation":
                patterns.add("pcsx");
                patterns.add("mednafen_psx");
                patterns.add("beetle_psx");
                break;
            case "psp":
                patterns.add("ppsspp");
                patterns.add("psp");
                break;
            case "genesis":
            case "megadrive":
            case "md":
                patterns.add("genesis");
                patterns.add("picodrive");
                break;
            case "wonderswan":
            case "wonderswancolor":
            case "ws":
            case "wsc":
                patterns.add("mednafen_wswan");
                patterns.add("wonderswan");
                break;
            case "ngp":
            case "ngpc":
                patterns.add("mednafen_ngp");
                patterns.add("ngp");
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
                break;

            default:
                // Pour les consoles non reconnues, essayer de trouver un pattern basé sur le
                // nom
                patterns.add(consoleKey);
                break;
        }

        return patterns;
    }

    /**
     * Retourne une liste de cores par défaut si cores.json n'est pas disponible
     */
    private List<CoreInfo> getFallbackCoresForConsole(String consoleKey) {
        List<CoreInfo> cores = new ArrayList<>();
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleKey);

        switch (canonicalId) {
            case "nes":
                cores.add(new CoreInfo("fceumm", "FCEUmm", "fceumm_libretro_android.so", "NES"));
                break;
            case "snes":
                cores.add(new CoreInfo("snes9x", "Snes9x", "snes9x_libretro_android.so", "SNES"));
                break;
            case "gb":
            case "gbc":
                cores.add(new CoreInfo("gambatte", "Gambatte", "gambatte_libretro_android.so", "GB/GBC"));
                break;
            case "gba":
                cores.add(new CoreInfo("mgba", "mGBA", "mgba_libretro_android.so", "GBA"));
                break;

            case "genesis":
            case "megadrive":
            case "md":
                cores.add(new CoreInfo("genesis_plus_gx", "Genesis Plus GX", "genesis_plus_gx_libretro_android.so",
                        "Genesis/MD"));
                cores.add(new CoreInfo("picodrive", "PicoDrive", "picodrive_libretro_android.so", "Genesis/32X"));
                break;
            case "psx":
            case "ps1":
            case "playstation":
                cores.add(new CoreInfo("pcsx_rearmed", "PCSX ReARMed", "pcsx_rearmed_libretro_android.so",
                        "PlayStation"));
                break;
            case "psp":
                cores.add(new CoreInfo("ppsspp", "PPSSPP", "ppsspp_libretro_android.so", "PSP"));
                break;
            case "arcade":
            case "mame":
            case "fbneo":
                cores.add(new CoreInfo("fbneo", "FBNeo", "fbneo_libretro_android.so", "Arcade"));
                cores.add(new CoreInfo("mame2003_plus", "MAME 2003-Plus", "mame2003_plus_libretro_android.so",
                        "Arcade (2003+)"));
                break;
            case "pce":
            case "pcengine":
                cores.add(new CoreInfo("mednafen_pce", "Mednafen PCE Fast", "mednafen_pce_libretro_android.so",
                        "PC Engine"));
                break;
            case "ngp":
                cores.add(new CoreInfo("mednafen_ngp", "Mednafen NGP", "mednafen_ngp_libretro_android.so",
                        "NeoGeo Pocket"));
                break;
            case "wonderswan":
            case "wonderswancolor":
                cores.add(new CoreInfo("mednafen_wswan", "Mednafen WSwan", "mednafen_wswan_libretro_android.so",
                        "WonderSwan"));
                break;
            default:
                // Pas de fallback pour les autres consoles
                break;
        }

        return cores;
    }
}

