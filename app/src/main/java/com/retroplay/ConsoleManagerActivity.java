package com.retroplay;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.retroplay.R;
import com.retroplay.usecases.LoadCoresUseCase;
import com.retroplay.usecases.ScanConsoleUseCase;
import com.retroplay.usecases.InstallCheatsUseCase;
import com.retroplay.usecases.ManageConsoleUseCase;
import com.retroplay.usecases.GenerateGamelistUseCase;
import com.retroplay.usecases.DownloadArtworksUseCase;
import com.retroplay.helpers.ConsoleConfigHelper;
import com.retroplay.helpers.GameLibraryPaths;
import com.retroplay.ui.adapters.ConsoleAdapter;
import com.retroplay.helpers.RomFileHelper;
import com.retroplay.models.ConsoleConfig;
import com.retroplay.models.ScannedRom;
import com.retroplay.utils.AssetFileHelper;
import com.retroplay.ui.dialogs.AuditReportDialog;
import com.retroplay.helpers.ConsoleNameHelper;
import com.retroplay.ConsoleNameMapper;
import com.retroplay.GamelistManager;

public class ConsoleManagerActivity extends AppCompatActivity {

    private static final String TAG = "ConsoleManagerActivity";

    private RecyclerView recyclerView;
    private ConsoleAdapter adapter;
    private List<ConsoleConfig> consoles = new ArrayList<>();
    private List<String> availableCores = new ArrayList<>();
    private String scrollToConsoleId = null; // Console à scroller automatiquement
    private InstallCheatsUseCase installCheatsUseCase;
    private ManageConsoleUseCase manageConsoleUseCase;
    private GenerateGamelistUseCase generateGamelistUseCase;
    private DownloadArtworksUseCase downloadArtworksUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console_manager);

        // Récupérer la console à scroller depuis l'intent
        if (getIntent() != null && getIntent().hasExtra("scrollToConsole")) {
            scrollToConsoleId = getIntent().getStringExtra("scrollToConsole");
            // Normaliser avec ConsoleNameMapper pour gérer les noms alternatifs
            if (scrollToConsoleId != null) {
                scrollToConsoleId = ConsoleNameMapper.normalizeToCanonical(scrollToConsoleId);
                Log.i(TAG, "Will scroll to console: " + scrollToConsoleId);
            }
        }

        setupViews();
        loadAvailableCores();
        loadConsoles();

        // Initialiser les Use Cases
        installCheatsUseCase = new InstallCheatsUseCase(this);
        manageConsoleUseCase = new ManageConsoleUseCase(this);
        generateGamelistUseCase = new GenerateGamelistUseCase(this);
        downloadArtworksUseCase = new DownloadArtworksUseCase(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les consoles pour avoir les infos à jour (nouveaux jeux, nouveaux
        // dossiers)
        loadConsoles();
    }

    private void setupViews() {
        TextView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.consolesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        MaterialButton updateAllArtworksButton = findViewById(R.id.updateAllArtworksButton);
        updateAllArtworksButton.setOnClickListener(v -> showGlobalArtworkDialog(false));

        MaterialButton fillMissingArtworksButton = findViewById(R.id.fillMissingArtworksButton);
        fillMissingArtworksButton.setOnClickListener(v -> showGlobalArtworkDialog(true));

        MaterialButton scanAllButton = findViewById(R.id.scanAllButton);
        scanAllButton.setOnClickListener(v -> scanAllConsoles());

        MaterialButton addButton = findViewById(R.id.addConsoleButton);
        addButton.setOnClickListener(v -> showAddConsoleDialog());

        MaterialButton installCheatsButton = findViewById(R.id.installCheatsButton);
        installCheatsButton.setOnClickListener(v -> installCheatsDatabase());
        installCheatsButton.setOnLongClickListener(v -> {
            showCheatStatus();
            return true;
        });

        MaterialButton manageCoresButton = findViewById(R.id.manageCoresButton);
        manageCoresButton.setOnClickListener(v -> openCoreManager());
    }

    private void openCoreManager() {
        Intent intent = new Intent(this, CoreManagerActivity.class);
        startActivity(intent);
    }

    /**
     * Afficher le statut des cheats installés
     */
    private void showCheatStatus() {
        com.retroplay.CheatInfo info = com.retroplay.CheatManager.INSTANCE.getCheatInfo();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Cheat Database Status");

        String message = "Current Mode: " + info.getMode().name() + "\n\n" + info.getDescription();

        if (info.getMode() != com.retroplay.CheatMode.NONE) {
            message += "\n\nYou can reinstall to change mode.";
        }

        builder.setMessage(message);

        if (info.getMode() != com.retroplay.CheatMode.NONE) {
            builder.setPositiveButton("REINSTALL", (dialog, which) -> installCheatsDatabase());
        }

        builder.setNegativeButton("CLOSE", null);
        builder.show();
    }

    private void scanAllConsoles() {
        if (consoles == null || consoles.isEmpty()) {
            android.widget.Toast.makeText(this, "No consoles to scan", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Afficher dialog de confirmation
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Scan All Consoles?")
                .setMessage("This will scan all " + consoles.size()
                        + " consoles and regenerate their gamelist.json files. This may take a while.")
                .setPositiveButton("Scan All", (dialog, which) -> {
                    // Afficher un progress dialog
                    android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                    progressDialog.setTitle("Scanning All Consoles...");
                    progressDialog.setMessage("0 / " + consoles.size());
                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMax(consoles.size());
                    progressDialog.setProgress(0);
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    // Scanner toutes les consoles en arrière-plan
                    new Thread(() -> {
                        int scanned = 0;
                        int success = 0;
                        StringBuilder auditReport = new StringBuilder();
                        auditReport.append("ROM AUDIT REPORT\n");
                        auditReport.append("================\n\n");

                        int totalRomsFound = 0;
                        int totalNewRoms = 0;
                        int totalUpdatedRoms = 0;
                        int totalRemovedRoms = 0;

                        for (ConsoleConfig console : consoles) {
                            final int currentIndex = scanned;
                            runOnUiThread(() -> {
                                progressDialog.setMessage(
                                        (currentIndex + 1) + " / " + consoles.size() + " - " + console.name);
                                progressDialog.setProgress(currentIndex);
                            });

                            try {
                                // Scanner ce répertoire (ROMs dans /roms/{console}/)
                                File consoleDir = new File(GameLibraryPaths.getRomsDirForConsole(console.id));
                                if (consoleDir.exists() && consoleDir.isDirectory()) {
                                    String extensions = console.extensions != null
                                            ? String.join(", ", console.extensions)
                                            : "";
                                    ScanConsoleUseCase.ScanResult result = new ScanConsoleUseCase()
                                            .scanConsoleWithAudit(console.id, console.name, extensions);
                                    if (result != null && result.success) {
                                        success++;
                                        totalRomsFound += result.totalRoms;
                                        totalNewRoms += result.newRoms;
                                        totalUpdatedRoms += result.updatedRoms;
                                        totalRemovedRoms += result.removedRoms;

                                        // Ajouter au rapport
                                        auditReport.append("[").append(console.name).append("]\n");
                                        auditReport.append("  Total ROMs: ").append(result.totalRoms).append("\n");
                                        if (result.newRoms > 0) {
                                            auditReport.append("  + New: ").append(result.newRoms).append("\n");
                                        }
                                        if (result.updatedRoms > 0) {
                                            auditReport.append("  ~ Updated: ").append(result.updatedRoms).append("\n");
                                        }
                                        if (result.removedRoms > 0) {
                                            auditReport.append("  - Removed: ").append(result.removedRoms)
                                                    .append(" (missing files)\n");
                                        }
                                        if (result.missingImages > 0) {
                                            auditReport.append("  ! Missing images: ").append(result.missingImages)
                                                    .append("\n");
                                        }
                                        auditReport.append("\n");
                                    }
                                }
                            } catch (Exception e) {
                                android.util.Log.e("ConsoleManager",
                                        "Error scanning " + console.id + ": " + e.getMessage());
                                auditReport.append("[").append(console.name).append("]\n");
                                auditReport.append("  ERROR: ").append(e.getMessage()).append("\n\n");
                            }

                            scanned++;

                            // Pause courte pour éviter de surcharger le système
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        // Résumé final
                        auditReport.append("SUMMARY\n");
                        auditReport.append("=======\n");
                        auditReport.append("Consoles scanned: ").append(success).append(" / ").append(scanned)
                                .append("\n");
                        auditReport.append("Total ROMs: ").append(totalRomsFound).append("\n");
                        auditReport.append("New ROMs added: ").append(totalNewRoms).append("\n");
                        auditReport.append("ROMs updated: ").append(totalUpdatedRoms).append("\n");
                        auditReport.append("ROMs removed: ").append(totalRemovedRoms).append("\n");

                        final int finalSuccess = success;
                        final int finalScanned = scanned;
                        final String finalReport = auditReport.toString();

                        runOnUiThread(() -> {
                            progressDialog.dismiss();

                            // Afficher le rapport d'audit
                            AuditReportDialog.show(ConsoleManagerActivity.this, finalReport, finalSuccess,
                                    finalScanned);

                            // Recharger la liste
                            loadConsoles();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Méthode showAuditReportDialog déplacée vers AuditReportDialog.show(...)

    // Méthode scanConsoleWithAudit déplacée vers ScanConsoleUseCase
    // Utiliser: new ScanConsoleUseCase().scanConsoleWithAudit(...)

    // Méthode scanConsoleSilently déplacée vers ScanConsoleUseCase
    // Utiliser: new ScanConsoleUseCase().scanConsoleSilently(...)

    private void loadAvailableCores() {
        LoadCoresUseCase useCase = new LoadCoresUseCase();
        useCase.loadAvailableCores(cores -> runOnUiThread(() -> {
            availableCores = cores;
            Log.i(TAG, "Loaded " + availableCores.size() + " available cores");
        }));
    }

    private void loadConsoles() {
        new Thread(() -> {
            try {
                // Scanner directement GameLibrary-Data/roms pour toutes les consoles
                File romsDir = new File(GameLibraryPaths.ROMS_DIR);
                List<ConsoleConfig> tempConsoles = new ArrayList<>();

                if (romsDir.exists() && romsDir.isDirectory()) {
                    File[] directories = romsDir.listFiles(File::isDirectory);
                    Log.i(TAG, "Scanning /roms/ directory, found " + (directories != null ? directories.length : 0)
                            + " directories");

                    if (directories != null) {
                        for (File dir : directories) {
                            String dirName = dir.getName();

                            // Ignorer les répertoires système (ne devrait pas être dans roms/, mais
                            // sécurité)
                            if (dirName.equals("data") || dirName.equals("emulatorjs") ||
                                    dirName.equals("vmnes") || dirName.equals("playlists") ||
                                    dirName.equals("saves") || dirName.equals("states") ||
                                    dirName.equals("cheats") || dirName.equals("media") ||
                                    dirName.equals("overlays") || dirName.equals("cores") ||
                                    dirName.equals("bios") || dirName.startsWith(".")) {
                                continue;
                            }

                            Log.d(TAG, "Scanning console directory: " + dirName);

                            // Scanner les sous-consoles (ex: fbneo/sega, fbneo/Taito) AVANT le parent
                            File[] subDirectories = dir.listFiles(File::isDirectory);
                            boolean hasSubconsoles = false;

                            if (subDirectories != null) {
                                Log.d(TAG, "  Found " + subDirectories.length + " subdirectories in " + dirName);
                            }

                            if (subDirectories != null) {
                                for (File subDir : subDirectories) {
                                    String subDirName = subDir.getName();

                                    // Ignorer les répertoires système
                                    if (subDirName.equals("media") || subDirName.equals("saves") ||
                                            subDirName.equals("states") || subDirName.equals("cheats") ||
                                            subDirName.equals("overlays") || subDirName.equals("cores") ||
                                            subDirName.equals("bios") || subDirName.startsWith(".")) {
                                        continue;
                                    }

                                    // Pour fbneo, accepter TOUS les sous-répertoires comme sous-consoles
                                    // (même s'ils n'ont pas encore de ROMs - ils peuvent être vides mais valides)
                                    boolean isFbneoSub = dirName.equals("fbneo");

                                    // Vérifier s'il y a des ROMs ou un gamelist.json dans ce sous-répertoire
                                    File[] romFiles = subDir.listFiles(file -> {
                                        String name = file.getName().toLowerCase();
                                        return file.isFile() && (name.endsWith(".zip") || name.endsWith(".bin") ||
                                                name.endsWith(".iso") || name.endsWith(".chd"));
                                    });

                                    File subGamelistFile = new File(subDir, "gamelist.json");
                                    boolean hasRoms = romFiles != null && romFiles.length > 0;
                                    boolean hasGamelist = subGamelistFile.exists();

                                    // Accepter si: a) a des ROMs/gamelist OU b) c'est un sous-dossier fbneo (même
                                    // vide)
                                    if (hasRoms || hasGamelist || isFbneoSub) {
                                        // C'est une sous-console valide
                                        String subConsoleId = dirName + "/" + subDirName;
                                        Log.d(TAG, "Tentative de chargement sous-console: " + subConsoleId +
                                                " (ROMs: " + (romFiles != null ? romFiles.length : 0) +
                                                ", Gamelist: " + hasGamelist + ")");
                                        ConsoleConfig subConfig = loadConsoleConfig(subDir, subConsoleId);
                                        if (subConfig != null) {
                                            tempConsoles.add(subConfig);
                                            hasSubconsoles = true;
                                            Log.i(TAG,
                                                    "✅ Sub-console chargée: " + subConsoleId + " -> " + subConfig.name);
                                        } else {
                                            Log.w(TAG, "❌ Échec chargement sous-console: " + subConsoleId
                                                    + " (loadConsoleConfig retourné null)");
                                        }
                                    } else {
                                        Log.d(TAG, "Sous-répertoire ignoré (pas de ROMs ni gamelist): " + dirName + "/"
                                                + subDirName);
                                    }
                                }
                            }

                            // Charger la console parent SEULEMENT si elle n'a PAS de sous-consoles
                            // Si elle a des sous-consoles, on n'affiche que les sous-consoles (comme
                            // GameListActivity)
                            if (!hasSubconsoles) {
                                ConsoleConfig parentConfig = loadConsoleConfig(dir, dirName);
                                if (parentConfig != null) {
                                    tempConsoles.add(parentConfig);
                                    Log.i(TAG, "Console chargée: " + dirName + " (display: " + parentConfig.name + ")");
                                }
                            } else {
                                Log.i(TAG, "Console parent " + dirName
                                        + " ignorée (a des sous-consoles, seul les sous-consoles seront affichées)");
                            }
                        }
                    }
                }

                // NE PAS faire de déduplication automatique - les répertoires sont des
                // variantes régionales
                // Exemple: "nes" (USA) et "famicom" (Japon) sont des consoles différentes avec
                // des ROMs différentes
                // Chaque répertoire doit être traité comme une console séparée

                // Log avant tri pour debug
                int subConsoleCount = 0;
                for (ConsoleConfig c : tempConsoles) {
                    if (c.id.contains("/")) {
                        subConsoleCount++;
                        Log.d(TAG, "Sous-console dans liste avant tri: " + c.id + " -> " + c.name);
                    }
                }
                Log.i(TAG, "Total consoles avant tri: " + tempConsoles.size() + " (dont " + subConsoleCount
                        + " sous-consoles)");

                // Trier pour regrouper les sous-consoles par parent (comme GameListActivity)
                // Les parents avec sous-consoles ne seront pas affichés, donc on trie juste
                // pour regrouper
                tempConsoles.sort((a, b) -> {
                    boolean aIsSub = a.id.contains("/");
                    boolean bIsSub = b.id.contains("/");

                    if (aIsSub && bIsSub) {
                        // Les deux sont des sous-consoles - comparer par parent puis par nom
                        String aParent = a.id.substring(0, a.id.indexOf("/"));
                        String bParent = b.id.substring(0, b.id.indexOf("/"));
                        int parentCompare = aParent.compareToIgnoreCase(bParent);
                        if (parentCompare != 0) {
                            return parentCompare;
                        }
                        // Même parent, comparer les noms de sous-consoles
                        String aSub = a.id.substring(a.id.indexOf("/") + 1);
                        String bSub = b.id.substring(b.id.indexOf("/") + 1);
                        return aSub.compareToIgnoreCase(bSub);
                    } else if (aIsSub) {
                        // a est une sous-console, b est un parent
                        // Les parents viennent avant leurs sous-consoles dans le tri
                        // mais ils ne seront pas affichés s'ils ont des sous-consoles
                        String aParent = a.id.substring(0, a.id.indexOf("/"));
                        int parentCompare = aParent.compareToIgnoreCase(b.id);
                        if (parentCompare == 0) {
                            return 1; // Sous-console après son parent
                        }
                        return parentCompare;
                    } else if (bIsSub) {
                        // b est une sous-console, a est un parent
                        String bParent = b.id.substring(0, b.id.indexOf("/"));
                        int parentCompare = a.id.compareToIgnoreCase(bParent);
                        if (parentCompare == 0) {
                            return -1; // Parent avant sa sous-console
                        }
                        return parentCompare;
                    } else {
                        // Les deux sont des parents - tri alphabétique simple
                        return a.id.compareToIgnoreCase(b.id);
                    }
                });

                // Log après tri pour debug
                Log.i(TAG, "Total consoles après tri: " + tempConsoles.size());
                for (ConsoleConfig c : tempConsoles) {
                    if (c.id.contains("/")) {
                        Log.d(TAG, "Sous-console dans liste après tri: " + c.id + " -> " + c.name);
                    }
                }

                runOnUiThread(() -> {
                    if (tempConsoles == null || tempConsoles.isEmpty()) {
                        Log.w(TAG, "Aucune console trouvée dans " + GameLibraryPaths.ROMS_DIR);
                        android.widget.Toast.makeText(this, "Aucune console trouvée", android.widget.Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }

                    consoles = tempConsoles;
                    Log.i(TAG, "Création de l'adapter avec " + consoles.size() + " consoles");
                    adapter = new ConsoleAdapter(consoles, new ConsoleAdapter.ConsoleActionsListener() {
                        @Override
                        public void onEditConsole(ConsoleConfig console) {
                            showEditConsoleDialog(console);
                        }

                        @Override
                        public void onRefreshConsole(ConsoleConfig console, String extensions,
                                GenerateGamelistUseCase.ProgressCallback progressCallback,
                                GenerateGamelistUseCase.PreviewCallback previewCallback) {
                            GenerateGamelistUseCase.PreviewCallback actualPreviewCallback = new GenerateGamelistUseCase.PreviewCallback() {
                                @Override
                                public void showPreview(String consoleId, List<ScannedRom> roms,
                                        boolean gamelistExists) {
                                    showGamelistPreviewDialog(consoleId, roms, gamelistExists);
                                }
                            };
                            generateGamelistUseCase.scanRomsAndGenerateGamelist(console.id, extensions,
                                    progressCallback, actualPreviewCallback);
                        }

                        @Override
                        public void onArtworkConsole(ConsoleConfig console) {
                            showArtworkOptionsForConsole(console);
                        }

                        @Override
                        public void runOnUiThread(Runnable action) {
                            ConsoleManagerActivity.this.runOnUiThread(action);
                        }
                    }, generateGamelistUseCase);

                    if (recyclerView == null) {
                        Log.e(TAG, "recyclerView est null!");
                        return;
                    }

                    if (adapter == null) {
                        Log.e(TAG, "adapter est null après création!");
                        return;
                    }

                    recyclerView.setAdapter(adapter);
                    Log.i(TAG, "Adapter assigné au RecyclerView. ItemCount: " + adapter.getItemCount());
                    Log.i(TAG, "Loaded " + consoles.size() + " consoles from GameLibrary-Data");
                    // Log détaillé pour debug
                    for (int i = 0; i < consoles.size(); i++) {
                        ConsoleConfig c = consoles.get(i);
                        Log.d(TAG, "Console[" + i + "]: " + c.id + " -> " + c.name
                                + (c.id.contains("/") ? " [SUB-CONSOLE]" : " [PARENT]"));
                    }

                    // Scroller vers la console spécifiée si demandé
                    if (scrollToConsoleId != null) {
                        scrollToConsole(scrollToConsoleId);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading consoles", e);
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Error loading consoles: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Scrolle vers une console spécifique dans la liste
     */
    private void scrollToConsole(String consoleId) {
        if (consoles == null || consoles.isEmpty() || consoleId == null) {
            return;
        }

        // Normaliser l'ID de console pour la comparaison
        String normalizedId = ConsoleNameMapper.normalizeToCanonical(consoleId);

        // Chercher la position de la console dans la liste
        int position = -1;
        for (int i = 0; i < consoles.size(); i++) {
            ConsoleConfig config = consoles.get(i);
            // Comparer avec l'ID normalisé
            String configId = ConsoleNameMapper.normalizeToCanonical(config.id);
            if (configId.equals(normalizedId)) {
                position = i;
                break;
            }
        }

        if (position >= 0) {
            // Scroller vers la position avec un délai pour s'assurer que le RecyclerView
            // est prêt
            final int finalPosition = position;
            final String finalConsoleId = consoleId;
            recyclerView.post(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(finalPosition, 0);
                    Log.i(TAG, "Scrolled to console: " + finalConsoleId + " at position " + finalPosition);
                }
            });
        } else {
            Log.w(TAG, "Console not found for scrolling: " + consoleId);
        }
    }

    /**
     * Scanner un répertoire de console et créer la config
     * Délégué à ConsoleConfigHelper
     */
    private ConsoleConfig loadConsoleConfig(File dir, String consoleId) {
        return ConsoleConfigHelper.loadConsoleConfig(dir, consoleId);
    }

    /**
     * Retourne le nom complet par défaut pour une console
     * Délégué à ConsoleConfigHelper
     */
    private String getDefaultFullName(String consoleId) {
        return ConsoleConfigHelper.getDefaultFullName(consoleId);
    }

    private void showAddConsoleDialog() {
        showEditConsoleDialog(null);
    }

    private void showEditConsoleDialog(ConsoleConfig existingConsole) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_console, null);

        EditText idInput = dialogView.findViewById(R.id.consoleIdInput);
        EditText nameInput = dialogView.findViewById(R.id.consoleNameInput);
        EditText fullNameInput = dialogView.findViewById(R.id.consoleFullNameInput);
        Spinner coreSpinner = dialogView.findViewById(R.id.coreSpinnerDialog);
        EditText extensionsInput = dialogView.findViewById(R.id.extensionsInput);
        EditText colorInput = dialogView.findViewById(R.id.colorInput);

        // Setup core spinner
        ArrayAdapter<String> coreAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, availableCores);
        coreAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        coreSpinner.setAdapter(coreAdapter);

        // If editing existing console, populate fields
        if (existingConsole != null) {
            idInput.setText(existingConsole.id);
            idInput.setEnabled(false); // Can't change ID
        } else {
            // New console: Auto-fill fields when ID loses focus
            idInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String id = idInput.getText().toString().trim();
                    if (!id.isEmpty()) {
                        String normalizedId = ConsoleNameMapper.normalizeToCanonical(id);

                        // Fill Name if empty
                        if (nameInput.getText().toString().isEmpty()) {
                            nameInput.setText(id.toUpperCase());
                        }

                        // Fill Full Name if empty
                        if (fullNameInput.getText().toString().isEmpty()) {
                            fullNameInput.setText(ConsoleNameMapper.getFullName(normalizedId));
                        }

                        // Fill Extensions if empty
                        if (extensionsInput.getText().toString().isEmpty()) {
                            java.util.List<String> exts = com.retroplay.GamelistManager.INSTANCE
                                    .getDefaultExtensions(normalizedId);
                            extensionsInput.setText(String.join(", ", exts));
                        }

                        // Fill Color if empty
                        if (colorInput.getText().toString().isEmpty()) {
                            colorInput.setText(ConsoleNameHelper.getConsoleColor(normalizedId));
                        }

                        // Select Default Core
                        String defaultCore = ConsoleNameHelper.getDefaultCore(normalizedId);
                        int corePos = availableCores.indexOf(defaultCore);
                        if (corePos >= 0) {
                            coreSpinner.setSelection(corePos);
                        }
                    }
                }
            });
        }

        if (existingConsole != null) {
            // Populate checks for existing console
            nameInput.setText(existingConsole.name);
            fullNameInput.setText(existingConsole.fullName);
            extensionsInput.setText(String.join(", ", existingConsole.extensions));
            colorInput.setText(existingConsole.color);

            int corePosition = availableCores.indexOf(existingConsole.defaultCore);
            if (corePosition >= 0) {
                coreSpinner.setSelection(corePosition);
            }
        }

        // Setup scanner button
        MaterialButton scannerButton = dialogView.findViewById(R.id.scannerButton);
        scannerButton.setOnClickListener(v -> {
            String id = idInput.getText().toString().trim();
            String extensions = extensionsInput.getText().toString().trim();

            if (id.isEmpty()) {
                android.widget.Toast.makeText(this, "Console ID is required to scan", android.widget.Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            // Lancer le scan
            GenerateGamelistUseCase.ProgressCallback progressCallback = new GenerateGamelistUseCase.ProgressCallback() {
                private android.app.ProgressDialog progressDialog;

                @Override
                public void showProgress(String title, String message, boolean indeterminate) {
                    runOnUiThread(() -> {
                        progressDialog = new android.app.ProgressDialog(ConsoleManagerActivity.this);
                        progressDialog.setTitle(title);
                        progressDialog.setMessage(message);
                        progressDialog.setIndeterminate(indeterminate);
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    });
                }

                @Override
                public void dismissProgress() {
                    runOnUiThread(() -> {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    });
                }

                @Override
                public void showToast(String message, int duration) {
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(ConsoleManagerActivity.this, message, duration).show();
                    });
                }
            };

            GenerateGamelistUseCase.PreviewCallback previewCallback = new GenerateGamelistUseCase.PreviewCallback() {
                @Override
                public void showPreview(String consoleId, List<ScannedRom> roms, boolean gamelistExists) {
                    showGamelistPreviewDialog(consoleId, roms, gamelistExists);
                }
            };

            generateGamelistUseCase.scanRomsAndGenerateGamelist(id, extensions, progressCallback, previewCallback);
        });

        builder.setView(dialogView);
        builder.setTitle(existingConsole == null ? "Add Console" : "Edit Console");

        builder.setPositiveButton("SAVE", (dialog, which) -> {
            String id = idInput.getText().toString().trim();
            String name = nameInput.getText().toString().trim();
            String fullName = fullNameInput.getText().toString().trim();
            String defaultCore = coreSpinner.getSelectedItem().toString();
            String extensions = extensionsInput.getText().toString().trim();
            String color = colorInput.getText().toString().trim();

            if (id.isEmpty() || name.isEmpty()) {
                android.widget.Toast.makeText(this, "ID and Name are required", android.widget.Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            saveConsoleConfig(id, name, fullName, defaultCore, extensions, color);
        });

        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void saveConsoleConfig(String id, String name, String fullName, String defaultCore,
            String extensions, String color) {
        new Thread(() -> {
            try {
                // Vérifier si le répertoire de la console existe (ROMs dans /roms/{console}/)
                File consoleDir = new File(GameLibraryPaths.getRomsDirForConsole(id));

                if (!consoleDir.exists()) {
                    runOnUiThread(() -> {
                        // Demander à l'utilisateur s'il veut créer le répertoire
                        new AlertDialog.Builder(this)
                                .setTitle("Directory not found")
                                .setMessage("The directory '" + id + "/' does not exist.\n\n" +
                                        "Path: " + consoleDir.getAbsolutePath() + "\n\n" +
                                        "Do you want to create it?")
                                .setPositiveButton("CREATE", (dialog, which) -> {
                                    createConsoleDirectory(consoleDir, id, name, fullName, defaultCore, extensions,
                                            color);
                                })
                                .setNegativeButton("CANCEL", null)
                                .show();
                    });
                    return;
                }

                // Vérifier si gamelist.json existe
                File gamelistFile = new File(consoleDir, "gamelist.json");
                if (!gamelistFile.exists()) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                                .setTitle("gamelist.json not found")
                                .setMessage("The file 'gamelist.json' is required in the console directory.\n\n" +
                                        "Please create it manually before configuring the console.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                    return;
                }

                // Create console.json content
                JSONObject config = new JSONObject();
                config.put("name", name);
                config.put("fullName", fullName);
                config.put("defaultCore", defaultCore);
                config.put("color", color);
                config.put("enabled", true);

                // Parse extensions
                JSONArray extsArray = new JSONArray();
                for (String ext : extensions.split(",")) {
                    extsArray.put(ext.trim());
                }
                config.put("extensions", extsArray);

                // Parse cores (for now, just use the default one)
                JSONArray coresArray = new JSONArray();
                coresArray.put(defaultCore);
                config.put("cores", coresArray);

                // Save to file
                // console.json reste à la racine de GameLibrary-Data (pas dans roms/)
                String filePath = GameLibraryPaths.getConsoleConfigPath(id);
                FileOutputStream fos = new FileOutputStream(filePath);
                fos.write(config.toString(2).getBytes("UTF-8"));
                fos.close();

                runOnUiThread(() -> {
                    android.widget.Toast
                            .makeText(this, "Console configuration saved: " + id, android.widget.Toast.LENGTH_SHORT)
                            .show();
                    loadConsoles(); // Reload list
                });

                Log.i(TAG, "Console config saved: " + filePath);

            } catch (Exception e) {
                Log.e(TAG, "Error saving console config", e);
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG)
                            .show();
                });
            }
        }).start();
    }

    private void createConsoleDirectory(File consoleDir, String id, String name, String fullName,
            String defaultCore, String extensions, String color) {
        new Thread(() -> {
            try {
                // Créer le répertoire
                if (!consoleDir.mkdirs()) {
                    throw new Exception("Failed to create directory");
                }

                // Créer un gamelist.json vide de base
                File gamelistFile = new File(consoleDir, "gamelist.json");
                JSONObject emptyGamelist = new JSONObject();
                JSONArray emptyGames = new JSONArray();
                emptyGamelist.put("games", emptyGames);

                FileOutputStream fos = new FileOutputStream(gamelistFile);
                fos.write(emptyGamelist.toString(2).getBytes("UTF-8"));
                fos.close();

                // Créer le répertoire media
                new File(consoleDir, "media/box2d").mkdirs();
                new File(consoleDir, "media/screenshot").mkdirs();

                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this,
                            "Directory created: " + id + "\nNow add ROMs and update gamelist.json",
                            android.widget.Toast.LENGTH_LONG).show();
                });

                // Maintenant sauvegarder la config
                saveConsoleConfig(id, name, fullName, defaultCore, extensions, color);

            } catch (Exception e) {
                Log.e(TAG, "Error creating console directory", e);
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Error creating directory: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Afficher un dialog de preview avec les ROMs trouvés et options Merge/Replace
     */
    private void showGamelistPreviewDialog(String consoleId, List<ScannedRom> roms, boolean gamelistExists) {
        // Construire le message de preview
        StringBuilder message = new StringBuilder();
        message.append("Found ").append(roms.size()).append(" ROM(s) in directory:\n\n");

        int maxPreview = Math.min(10, roms.size());
        for (int i = 0; i < maxPreview; i++) {
            ScannedRom rom = roms.get(i);
            message.append("- ").append(rom.name);
            if (rom.hasBox2dImage || rom.hasScreenshot) {
                message.append(" [");
                if (rom.hasBox2dImage)
                    message.append("BOX");
                if (rom.hasBox2dImage && rom.hasScreenshot)
                    message.append(", ");
                if (rom.hasScreenshot)
                    message.append("SCREEN");
                message.append("]");
            }
            message.append("\n");
        }

        if (roms.size() > maxPreview) {
            message.append("... and ").append(roms.size() - maxPreview).append(" more\n");
        }

        message.append("\n\nChoose an option:");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Gamelist Preview");
        builder.setMessage(message.toString());

        if (gamelistExists) {
            builder.setPositiveButton("MERGE", (dialog, which) -> {
                saveGeneratedGamelist(consoleId, roms, true);
            });
            builder.setNeutralButton("REPLACE", (dialog, which) -> {
                saveGeneratedGamelist(consoleId, roms, false);
            });
        } else {
            builder.setPositiveButton("CREATE", (dialog, which) -> {
                saveGeneratedGamelist(consoleId, roms, false);
            });
        }

        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    /**
     * Sauvegarder le gamelist.json généré
     */
    private void saveGeneratedGamelist(String consoleId, List<ScannedRom> roms, boolean merge) {
        GenerateGamelistUseCase.ProgressCallback progressCallback = new GenerateGamelistUseCase.ProgressCallback() {
            private android.app.ProgressDialog progressDialog;

            @Override
            public void showProgress(String title, String message, boolean indeterminate) {
                runOnUiThread(() -> {
                    progressDialog = new android.app.ProgressDialog(ConsoleManagerActivity.this);
                    progressDialog.setTitle(title);
                    progressDialog.setMessage(message);
                    progressDialog.setIndeterminate(indeterminate);
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                });
            }

            @Override
            public void dismissProgress() {
                runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                });
            }

            @Override
            public void showToast(String message, int duration) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(ConsoleManagerActivity.this, message, duration).show();
                });
            }
        };

        GenerateGamelistUseCase.SaveCallback saveCallback = new GenerateGamelistUseCase.SaveCallback() {
            @Override
            public void onGamelistSaved(String consoleId, int romsAdded) {
                // Recharger la liste des consoles pour mettre à jour le statut hasGamelist
                loadConsoles();

                // Retourner un résultat pour que GameListActivity se rafraîchisse
                Intent resultIntent = new Intent();
                resultIntent.putExtra("gamelistGenerated", true);
                resultIntent.putExtra("consoleId", consoleId);
                setResult(RESULT_OK, resultIntent);
            }
        };

        generateGamelistUseCase.saveGeneratedGamelist(consoleId, roms, merge, progressCallback, saveCallback);
    }

    /**
     * Vérifier si un fichier est un ROM (utilise la logique de RomScanner)
     */

    /**
     * Classe pour stocker les infos d'un ROM scanné
     */
    // ScannedRom déplacé vers com.retroplay.models.ScannedRom

    // Console config data class
    // ConsoleConfig déplacé vers com.retroplay.models.ConsoleConfig

    // AuditResult déplacé vers ScanConsoleUseCase.ScanResult

    // RecyclerView Adapter

    /**
     * Installer la base de données de cheats depuis les assets
     */
    private void installCheatsDatabase() {
        InstallCheatsUseCase.DialogCallback dialogCallback = new InstallCheatsUseCase.DialogCallback() {
            @Override
            public void showInstallOptions() {
                showCheatsInstallOptions();
            }

            @Override
            public void showSystemSelector() {
                showSystemSelector();
            }

            @Override
            public void showReinstallDialog(String message, Runnable onReinstall) {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                        ConsoleManagerActivity.this);
                builder.setTitle("Reinstall Cheats");
                builder.setMessage(message);
                builder.setPositiveButton("REINSTALL", (dialog, which) -> onReinstall.run());
                builder.setNegativeButton("CANCEL", null);
                builder.show();
            }

            @Override
            public void showAlreadyInstalledDialog(String message, Runnable onReinstall) {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                        ConsoleManagerActivity.this);
                builder.setTitle("Cheats Already Installed");
                builder.setMessage(message);
                builder.setPositiveButton("REINSTALL", (dialog, which) -> onReinstall.run());
                builder.setNegativeButton("CANCEL", null);
                builder.show();
            }
        };

        InstallCheatsUseCase.ProgressCallback progressCallback = createProgressCallback();

        installCheatsUseCase.checkAndInstallCheats(dialogCallback, progressCallback);
    }

    /**
     * Crée un ProgressCallback pour InstallCheatsUseCase
     */
    private InstallCheatsUseCase.ProgressCallback createProgressCallback() {
        return new InstallCheatsUseCase.ProgressCallback() {
            private android.app.ProgressDialog progressDialog;

            @Override
            public void showProgress(String title, String message, int max, boolean indeterminate) {
                runOnUiThread(() -> {
                    progressDialog = new android.app.ProgressDialog(ConsoleManagerActivity.this);
                    progressDialog.setTitle(title);
                    progressDialog.setMessage(message);
                    if (indeterminate) {
                        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
                    } else {
                        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                        progressDialog.setMax(max);
                        progressDialog.setProgress(0);
                    }
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                });
            }

            @Override
            public void updateProgress(int progress, String message) {
                runOnUiThread(() -> {
                    if (progressDialog != null) {
                        if (progress >= 0) {
                            progressDialog.setProgress(progress);
                        }
                        if (message != null) {
                            progressDialog.setMessage(message);
                        }
                    }
                });
            }

            @Override
            public void dismissProgress() {
                runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                });
            }

            @Override
            public void showToast(String message, int duration) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(ConsoleManagerActivity.this, message, duration).show();
                });
            }
        };
    }

    /**
     * Afficher les options d'installation des cheats
     */
    private void showCheatsInstallOptions() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Install Cheats Database");
        builder.setMessage("Choose installation method:\n\n" +
                "1. ZIP MODE (Recommended)\n" +
                "   - Instant installation\n" +
                "   - 14 MB storage\n" +
                "   - Read on-the-fly\n\n" +
                "2. EXTRACT ALL\n" +
                "   - 2-3 minutes installation\n" +
                "   - 45 MB storage\n" +
                "   - Faster game loading\n\n" +
                "3. EXTRACT BY SYSTEM\n" +
                "   - Choose which systems\n" +
                "   - Custom storage size");

        builder.setPositiveButton("ZIP MODE", (dialog, which) -> {
            installCheatsUseCase.copyZipToDevice(createProgressCallback());
        });

        builder.setNeutralButton("EXTRACT ALL", (dialog, which) -> {
            InstallCheatsUseCase.ProgressCallback progressCallback = createProgressCallback();
            InstallCheatsUseCase.SystemProgressCallback systemCallback = new InstallCheatsUseCase.SystemProgressCallback() {
                @Override
                public void onSystemStart(String systemName, int systemIndex, int totalSystems) {
                    // Utilisé par AssetFileHelper pour la copie depuis assets (fallback)
                }

                @Override
                public void onFileProgress(String fileName) {
                    // Utilisé par AssetFileHelper pour la progression
                }
            };
            installCheatsUseCase.performCheatsInstallation(progressCallback, systemCallback);
        });

        builder.setNegativeButton("BY SYSTEM", (dialog, which) -> {
            showSystemSelector();
        });

        builder.show();
    }

    /**
     * Afficher le sélecteur de systèmes pour extraction partielle
     */
    private void showSystemSelector() {
        String[] systems = InstallCheatsUseCase.getAvailableSystems();
        boolean[] selectedSystems = new boolean[systems.length];

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Systems");
        builder.setMultiChoiceItems(systems, selectedSystems, (dialog, which, isChecked) -> {
            selectedSystems[which] = isChecked;
        });

        builder.setPositiveButton("INSTALL", (dialog, which) -> {
            // Compter les systèmes sélectionnés
            int count = 0;
            for (boolean selected : selectedSystems) {
                if (selected)
                    count++;
            }

            if (count == 0) {
                android.widget.Toast.makeText(this, "No systems selected", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Construire la liste des systèmes sélectionnés
            java.util.List<String> selectedSystemsList = new java.util.ArrayList<>();
            for (int i = 0; i < systems.length; i++) {
                if (selectedSystems[i]) {
                    selectedSystemsList.add(InstallCheatsUseCase.getSystemFolderName(systems[i]));
                }
            }

            installCheatsUseCase.extractSelectedSystems(selectedSystemsList, createProgressCallback());
        });

        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    // File I/O methods déplacées vers com.retroplay.utils.AssetFileHelper

    private void showGlobalArtworkDialog(boolean missingOnly) {
        if (consoles == null || consoles.isEmpty()) {
            android.widget.Toast.makeText(this, "No consoles configured", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String title = missingOnly ? "Download Missing Artwork" : "Refresh All Artwork";
        String message = missingOnly ? "Download missing boxarts and screenshots for all consoles?"
                : "Download and replace all boxarts and screenshots for all consoles?";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Start", (dialog, which) -> {
                    DownloadArtworksUseCase.ProgressCallback progressCallback = new DownloadArtworksUseCase.ProgressCallback() {
                        private android.app.ProgressDialog progressDialog;

                        @Override
                        public void showProgress(String title, String message, int max, boolean indeterminate) {
                            runOnUiThread(() -> {
                                progressDialog = new android.app.ProgressDialog(ConsoleManagerActivity.this);
                                progressDialog.setTitle(title);
                                progressDialog.setMessage(message);
                                if (indeterminate) {
                                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
                                    progressDialog.setIndeterminate(true);
                                } else {
                                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                                    progressDialog.setMax(max);
                                    progressDialog.setProgress(0);
                                }
                                progressDialog.setCancelable(false);
                                progressDialog.show();
                            });
                        }

                        @Override
                        public void updateProgress(int progress, String message) {
                            runOnUiThread(() -> {
                                if (progressDialog != null) {
                                    if (progress >= 0) {
                                        progressDialog.setProgress(progress);
                                    }
                                    if (message != null) {
                                        progressDialog.setMessage(message);
                                    }
                                }
                            });
                        }

                        @Override
                        public void dismissProgress() {
                            runOnUiThread(() -> {
                                if (progressDialog != null) {
                                    progressDialog.dismiss();
                                    progressDialog = null;
                                }
                            });
                        }

                        @Override
                        public void showToast(String message, int duration) {
                            runOnUiThread(() -> {
                                android.widget.Toast.makeText(ConsoleManagerActivity.this, message, duration).show();
                            });
                        }

                        @Override
                        public void showResultDialog(String title, String message) {
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(ConsoleManagerActivity.this)
                                        .setTitle(title)
                                        .setMessage(message)
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                        }
                    };

                    downloadArtworksUseCase.downloadArtworksForConsoles(new ArrayList<>(consoles), missingOnly,
                            progressCallback);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showArtworkOptionsForConsole(ConsoleConfig console) {
        if (console == null) {
            return;
        }
        String[] options = new String[] { "Download missing", "Download all" };
        new AlertDialog.Builder(this)
                .setTitle("Artwork - " + console.name)
                .setItems(options, (dialog, which) -> {
                    DownloadArtworksUseCase.ProgressCallback progressCallback = new DownloadArtworksUseCase.ProgressCallback() {
                        private android.app.ProgressDialog progressDialog;

                        @Override
                        public void showProgress(String title, String message, int max, boolean indeterminate) {
                            runOnUiThread(() -> {
                                progressDialog = new android.app.ProgressDialog(ConsoleManagerActivity.this);
                                progressDialog.setTitle(title);
                                progressDialog.setMessage(message);
                                if (indeterminate) {
                                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
                                    progressDialog.setIndeterminate(true);
                                } else {
                                    progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                                    progressDialog.setMax(max);
                                    progressDialog.setProgress(0);
                                }
                                progressDialog.setCancelable(false);
                                progressDialog.show();
                            });
                        }

                        @Override
                        public void updateProgress(int progress, String message) {
                            runOnUiThread(() -> {
                                if (progressDialog != null) {
                                    if (progress >= 0) {
                                        progressDialog.setProgress(progress);
                                    }
                                    if (message != null) {
                                        progressDialog.setMessage(message);
                                    }
                                }
                            });
                        }

                        @Override
                        public void dismissProgress() {
                            runOnUiThread(() -> {
                                if (progressDialog != null) {
                                    progressDialog.dismiss();
                                    progressDialog = null;
                                }
                            });
                        }

                        @Override
                        public void showToast(String message, int duration) {
                            runOnUiThread(() -> {
                                android.widget.Toast.makeText(ConsoleManagerActivity.this, message, duration).show();
                            });
                        }

                        @Override
                        public void showResultDialog(String title, String message) {
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(ConsoleManagerActivity.this)
                                        .setTitle(title)
                                        .setMessage(message)
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                        }
                    };

                    if (which == 0) {
                        downloadArtworksUseCase.downloadArtworksForConsoles(Collections.singletonList(console), true,
                                progressCallback);
                    } else if (which == 1) {
                        downloadArtworksUseCase.downloadArtworksForConsoles(Collections.singletonList(console), false,
                                progressCallback);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

}
