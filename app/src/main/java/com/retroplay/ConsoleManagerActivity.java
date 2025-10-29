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
import java.util.List;
import com.retroplay.R;

public class ConsoleManagerActivity extends AppCompatActivity {
    
    private static final String TAG = "ConsoleManagerActivity";
    // Répertoire partagé pour les ROMs et données de consoles
    private static final String GAMELIBRARY_DIR = "/storage/emulated/0/GameLibrary-Data";
    
    private RecyclerView recyclerView;
    private ConsoleAdapter adapter;
    private List<ConsoleConfig> consoles = new ArrayList<>();
    private List<String> availableCores = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console_manager);
        
        setupViews();
        loadAvailableCores();
        loadConsoles();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les consoles pour avoir les infos à jour (nouveaux jeux, nouveaux dossiers)
        loadConsoles();
    }
    
    private void setupViews() {
        TextView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
        
        recyclerView = findViewById(R.id.consolesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        MaterialButton scanAllButton = findViewById(R.id.scanAllButton);
        scanAllButton.setOnClickListener(v -> scanAllConsoles());
        
        MaterialButton importXmlButton = findViewById(R.id.importXmlButton);
        importXmlButton.setOnClickListener(v -> showImportXmlDialog());
        
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
        CheatInfo info = CheatManager.INSTANCE.getCheatInfo();
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Cheat Database Status");
        
        String message = "Current Mode: " + info.getMode().name() + "\n\n" + info.getDescription();
        
        if (info.getMode() != CheatMode.NONE) {
            message += "\n\nYou can reinstall to change mode.";
        }
        
        builder.setMessage(message);
        
        if (info.getMode() != CheatMode.NONE) {
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
            .setMessage("This will scan all " + consoles.size() + " consoles and regenerate their gamelist.json files. This may take a while.")
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
                            progressDialog.setMessage((currentIndex + 1) + " / " + consoles.size() + " - " + console.name);
                            progressDialog.setProgress(currentIndex);
                        });
                        
                        try {
                            // Scanner ce répertoire
                            File consoleDir = new File(GAMELIBRARY_DIR + "/" + console.id);
                            if (consoleDir.exists() && consoleDir.isDirectory()) {
                                String extensions = console.extensions != null ? String.join(", ", console.extensions) : "";
                                AuditResult result = scanConsoleWithAudit(console.id, console.name, extensions);
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
                                        auditReport.append("  - Removed: ").append(result.removedRoms).append(" (missing files)\n");
                                    }
                                    if (result.missingImages > 0) {
                                        auditReport.append("  ! Missing images: ").append(result.missingImages).append("\n");
                                    }
                                    auditReport.append("\n");
                                }
                            }
                        } catch (Exception e) {
                            android.util.Log.e("ConsoleManager", "Error scanning " + console.id + ": " + e.getMessage());
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
                    auditReport.append("Consoles scanned: ").append(success).append(" / ").append(scanned).append("\n");
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
                        showAuditReportDialog(finalReport, finalSuccess, finalScanned);
                        
                        // Recharger la liste
                        loadConsoles();
                    });
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showAuditReportDialog(String report, int success, int total) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("ROM Audit Report");
        
        // Créer un ScrollView pour le rapport
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(report);
        textView.setTextSize(12);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Copy Report", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Audit Report", report);
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(this, "Report copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }
    
    /**
     * Shows dialog to select consoles for XML metadata import
     */
    private void showImportXmlDialog() {
        // Filter consoles that have both XML and JSON
        List<ConsoleConfig> importableConsoles = new ArrayList<>();
        for (ConsoleConfig console : consoles) {
            File xmlFile = new File(GAMELIBRARY_DIR + "/" + console.id + "/gamelist.xml");
            File jsonFile = new File(GAMELIBRARY_DIR + "/" + console.id + "/gamelist.json");
            if (xmlFile.exists() && jsonFile.exists()) {
                importableConsoles.add(console);
            }
        }
        
        if (importableConsoles.isEmpty()) {
            android.widget.Toast.makeText(this, "No consoles with both XML and JSON found", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        
        // Create selection dialog
        String[] consoleNames = new String[importableConsoles.size() + 1];
        consoleNames[0] = "ALL CONSOLES (" + importableConsoles.size() + ")";
        for (int i = 0; i < importableConsoles.size(); i++) {
            consoleNames[i + 1] = importableConsoles.get(i).name + " (" + importableConsoles.get(i).id + ")";
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Import XML Metadata")
            .setItems(consoleNames, (dialog, which) -> {
                if (which == 0) {
                    // Import all
                    importXmlMetadataAll(importableConsoles);
                } else {
                    // Import single console
                    ConsoleConfig selected = importableConsoles.get(which - 1);
                    importXmlMetadataForConsole(selected);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Import XML metadata for all consoles
     */
    private void importXmlMetadataAll(List<ConsoleConfig> consolesToImport) {
        // Show confirmation
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Import XML Metadata for All?")
            .setMessage("This will enrich " + consolesToImport.size() + " consoles with metadata from gamelist.xml files.\n\nBackups will be created automatically.")
            .setPositiveButton("Import All", (dialog, which) -> {
                // Progress dialog
                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                progressDialog.setTitle("Importing XML Metadata...");
                progressDialog.setMessage("0 / " + consolesToImport.size());
                progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(consolesToImport.size());
                progressDialog.setProgress(0);
                progressDialog.setCancelable(false);
                progressDialog.show();
                
                // Import in background
                new Thread(() -> {
                    int imported = 0;
                    int success = 0;
                    int totalEnriched = 0;
                    StringBuilder report = new StringBuilder();
                    report.append("XML METADATA IMPORT REPORT\n");
                    report.append("==========================\n\n");
                    
                    for (ConsoleConfig console : consolesToImport) {
                        final int currentIndex = imported;
                        runOnUiThread(() -> {
                            progressDialog.setMessage((currentIndex + 1) + " / " + consolesToImport.size() + " - " + console.name);
                            progressDialog.setProgress(currentIndex);
                        });
                        
                        try {
                            ImportResult result = importXmlMetadataSync(console.id, console.name);
                            if (result != null && result.success) {
                                success++;
                                totalEnriched += result.enrichedCount;
                                
                                report.append("[").append(console.name).append("]\n");
                                report.append("  Games enriched: ").append(result.enrichedCount).append(" / ").append(result.totalGames).append("\n");
                                report.append("  Backup: gamelist_backup_").append(result.timestamp).append(".json\n\n");
                            } else {
                                report.append("[").append(console.name).append("]\n");
                                report.append("  ERROR: ").append(result != null ? result.error : "Unknown error").append("\n\n");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error importing " + console.id, e);
                            report.append("[").append(console.name).append("]\n");
                            report.append("  ERROR: ").append(e.getMessage()).append("\n\n");
                        }
                        
                        imported++;
                        
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    // Summary
                    report.append("SUMMARY\n");
                    report.append("=======\n");
                    report.append("Consoles imported: ").append(success).append(" / ").append(imported).append("\n");
                    report.append("Total games enriched: ").append(totalEnriched).append("\n");
                    
                    final String finalReport = report.toString();
                    final int finalSuccess = success;
                    final int finalImported = imported;
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        showImportReportDialog(finalReport, finalSuccess, finalImported);
                    });
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Import XML metadata for a single console
     */
    private void importXmlMetadataForConsole(ConsoleConfig console) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Import XML Metadata")
            .setMessage("Import metadata for " + console.name + " from gamelist.xml?\n\nA backup will be created automatically.")
            .setPositiveButton("Import", (dialog, which) -> {
                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                progressDialog.setMessage("Importing metadata for " + console.name + "...");
                progressDialog.setCancelable(false);
                progressDialog.show();
                
                new Thread(() -> {
                    ImportResult result = importXmlMetadataSync(console.id, console.name);
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (result != null && result.success) {
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Import Successful")
                                .setMessage("Enriched " + result.enrichedCount + " / " + result.totalGames + " games\n\nBackup: gamelist_backup_" + result.timestamp + ".json")
                                .setPositiveButton("OK", null)
                                .show();
                        } else {
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Import Failed")
                                .setMessage("Error: " + (result != null ? result.error : "Unknown error"))
                                .setPositiveButton("OK", null)
                                .show();
                        }
                    });
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Import XML metadata synchronously (call from background thread)
     */
    private ImportResult importXmlMetadataSync(String consoleId, String consoleName) {
        ImportResult result = new ImportResult();
        result.consoleId = consoleId;
        result.consoleName = consoleName;
        result.timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(new java.util.Date());
        
        try {
            File consoleDir = new File(GAMELIBRARY_DIR + "/" + consoleId);
            File xmlFile = new File(consoleDir, "gamelist.xml");
            File jsonFile = new File(consoleDir, "gamelist.json");
            
            if (!xmlFile.exists()) {
                result.error = "gamelist.xml not found";
                return result;
            }
            
            if (!jsonFile.exists()) {
                result.error = "gamelist.json not found";
                return result;
            }
            
            Log.i(TAG, "Importing XML metadata for: " + consoleId);
            
            // Parse XML
            java.util.Map<String, XmlGameData> xmlGames = parseXmlGamelist(xmlFile);
            if (xmlGames.isEmpty()) {
                result.error = "No games found in XML";
                return result;
            }
            
            Log.i(TAG, "Parsed " + xmlGames.size() + " games from XML");
            
            // Parse JSON
            org.json.JSONObject jsonData = new org.json.JSONObject(readFileToString(jsonFile));
            org.json.JSONArray gamesArray = jsonData.getJSONArray("games");
            
            result.totalGames = gamesArray.length();
            
            // Create backup
            File backupFile = new File(consoleDir, "gamelist_backup_" + result.timestamp + ".json");
            copyFile(jsonFile, backupFile);
            Log.i(TAG, "Backup created: " + backupFile.getName());
            
            // Enrich JSON games with XML metadata
            // Détecter si c'est une console arcade (set names courts)
            boolean isArcadeConsole = consoleId.contains("arcade") || consoleId.contains("mame") 
                                   || consoleId.contains("fbneo") || consoleId.contains("cps") 
                                   || consoleId.contains("neogeo");
            
            for (int i = 0; i < gamesArray.length(); i++) {
                org.json.JSONObject game = gamesArray.getJSONObject(i);
                String path = game.getString("path").replace("./", "");
                
                if (xmlGames.containsKey(path)) {
                    XmlGameData xmlGame = xmlGames.get(path);
                    boolean wasEnriched = false;
                    
                    // Enrich name field
                    // Pour arcade: toujours remplacer (set names courts -> vrais titres)
                    // Pour autres consoles: remplacer seulement si vide
                    if (!xmlGame.name.isEmpty()) {
                        if (isArcadeConsole || game.optString("name", "").isEmpty()) {
                            game.put("name", xmlGame.name);
                            wasEnriched = true;
                        }
                    }
                    
                    if (game.optString("desc", "").isEmpty() && !xmlGame.desc.isEmpty()) {
                        game.put("desc", xmlGame.desc);
                        wasEnriched = true;
                    }
                    
                    if (game.optString("releasedate", "").isEmpty() && !xmlGame.releasedate.isEmpty()) {
                        game.put("releasedate", xmlGame.releasedate);
                        wasEnriched = true;
                    }
                    
                    if (game.optString("developer", "").isEmpty() && !xmlGame.developer.isEmpty()) {
                        game.put("developer", xmlGame.developer);
                        wasEnriched = true;
                    }
                    
                    if (game.optString("publisher", "").isEmpty() && !xmlGame.publisher.isEmpty()) {
                        game.put("publisher", xmlGame.publisher);
                        wasEnriched = true;
                    }
                    
                    if (game.optString("genre", "").isEmpty() && !xmlGame.genre.isEmpty()) {
                        game.put("genre", xmlGame.genre);
                        wasEnriched = true;
                    }
                    
                    if (game.optString("players", "").isEmpty() && !xmlGame.players.isEmpty()) {
                        game.put("players", xmlGame.players);
                        wasEnriched = true;
                    }
                    
                    if (!xmlGame.rating.isEmpty()) {
                        game.put("rating", xmlGame.rating);
                        wasEnriched = true;
                    }
                    
                    if (wasEnriched) {
                        result.enrichedCount++;
                    }
                }
            }
            
            // Write enriched JSON back to file
            String enrichedJson = jsonData.toString(2); // Pretty print with indent=2
            writeStringToFile(jsonFile, enrichedJson);
            
            Log.i(TAG, "Import successful: " + result.enrichedCount + " games enriched");
            result.success = true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error importing XML for " + consoleId, e);
            result.error = e.getMessage();
            result.success = false;
        }
        
        return result;
    }
    
    /**
     * Parse XML gamelist file
     */
    private java.util.Map<String, XmlGameData> parseXmlGamelist(File xmlFile) {
        java.util.Map<String, XmlGameData> games = new java.util.HashMap<>();
        
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();
            
            org.w3c.dom.NodeList gameNodes = doc.getElementsByTagName("game");
            
            for (int i = 0; i < gameNodes.getLength(); i++) {
                org.w3c.dom.Node node = gameNodes.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    
                    XmlGameData gameData = new XmlGameData();
                    gameData.path = getXmlElementText(element, "path").replace("./", "");
                    gameData.name = getXmlElementText(element, "name");
                    gameData.desc = getXmlElementText(element, "desc");
                    gameData.releasedate = getXmlElementText(element, "releasedate");
                    gameData.developer = getXmlElementText(element, "developer");
                    gameData.publisher = getXmlElementText(element, "publisher");
                    gameData.genre = getXmlElementText(element, "genre");
                    gameData.players = getXmlElementText(element, "players");
                    gameData.rating = getXmlElementText(element, "rating");
                    
                    if (!gameData.path.isEmpty()) {
                        games.put(gameData.path, gameData);
                    }
                }
            }
            
            Log.i(TAG, "Parsed " + games.size() + " games from XML");
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing XML", e);
        }
        
        return games;
    }
    
    /**
     * Get text content from XML element
     */
    private String getXmlElementText(org.w3c.dom.Element parent, String tagName) {
        org.w3c.dom.NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            org.w3c.dom.Node node = nodes.item(0);
            String text = node.getTextContent();
            return text != null ? text.trim() : "";
        }
        return "";
    }
    
    /**
     * Show import report dialog
     */
    private void showImportReportDialog(String report, int success, int total) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("XML Import Report");
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(report);
        textView.setTextSize(12);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Copy Report", (dialog2, which2) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Import Report", report);
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(this, "Report copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }
    
    /**
     * Helper class to hold XML game data
     */
    private static class XmlGameData {
        String path = "";
        String name = "";
        String desc = "";
        String releasedate = "";
        String developer = "";
        String publisher = "";
        String genre = "";
        String players = "";
        String rating = "";
    }
    
    /**
     * Helper class to hold import result
     */
    private static class ImportResult {
        String consoleId;
        String consoleName;
        boolean success = false;
        String error = "";
        int totalGames = 0;
        int enrichedCount = 0;
        String timestamp = "";
    }
    
    /**
     * Read file to string
     */
    private String readFileToString(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }
    
    /**
     * Write string to file
     */
    private void writeStringToFile(File file, String content) throws Exception {
        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write(content);
        writer.close();
    }
    
    /**
     * Copy file
     */
    private void copyFile(File source, File dest) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(source);
        java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
        in.close();
        out.close();
    }
    
    private AuditResult scanConsoleWithAudit(String consoleId, String consoleName, String extensionsStr) {
        AuditResult result = new AuditResult();
        result.consoleId = consoleId;
        result.consoleName = consoleName;
        
        try {
            File consoleDir = new File(GAMELIBRARY_DIR + "/" + consoleId);
            if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                result.success = false;
                return result;
            }
            
            // Charger les extensions depuis console.json si présent
            List<String> extensions = new ArrayList<>();
            File consoleJsonFile = new File(consoleDir, "console.json");
            if (consoleJsonFile.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(consoleJsonFile);
                byte[] buffer = new byte[(int) consoleJsonFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer, "UTF-8");
                JSONObject consoleJson = new JSONObject(json);
                
                if (consoleJson.has("extensions")) {
                    JSONArray extArray = consoleJson.getJSONArray("extensions");
                    for (int i = 0; i < extArray.length(); i++) {
                        extensions.add(extArray.getString(i).toLowerCase());
                    }
                }
            }
            
            // Si pas d'extensions dans console.json, utiliser celles fournies
            if (extensions.isEmpty() && !extensionsStr.isEmpty()) {
                String[] parts = extensionsStr.split(",");
                for (String ext : parts) {
                    String cleaned = ext.trim().toLowerCase();
                    if (!cleaned.isEmpty()) {
                        if (!cleaned.startsWith(".")) {
                            cleaned = "." + cleaned;
                        }
                        extensions.add(cleaned);
                    }
                }
            }
            
            // Scanner les ROMs dans le répertoire
            File[] files = consoleDir.listFiles();
            List<ScannedRom> scannedRoms = new ArrayList<>();
            int romCounter = 1;
            
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String fileName = file.getName();
                        
                        // Vérifier si c'est un ROM
                        boolean isRom = false;
                        if (!extensions.isEmpty()) {
                            for (String ext : extensions) {
                                if (fileName.toLowerCase().endsWith(ext)) {
                                    isRom = true;
                                    break;
                                }
                            }
                        } else {
                            isRom = isRomFile(fileName);
                        }
                        
                        if (isRom) {
                            String baseName = getBaseNameFromFile(fileName);
                            
                            // Vérifier si les images existent
                            File box2dImage = new File(consoleDir, "media/box2d/" + baseName + ".png");
                            File screenshotImage = new File(consoleDir, "media/screenshots/" + baseName + ".png");
                            
                            ScannedRom rom = new ScannedRom();
                            rom.id = String.valueOf(romCounter++);
                            rom.name = baseName;
                            rom.path = "./" + fileName;
                            rom.hasBox2dImage = box2dImage.exists();
                            rom.hasScreenshot = screenshotImage.exists();
                            
                            scannedRoms.add(rom);
                        }
                    }
                }
            }
            
            result.totalRoms = scannedRoms.size();
            
            // Si aucun ROM trouvé, ignorer
            if (scannedRoms.isEmpty()) {
                result.success = false;
                return result;
            }
            
            // AUDIT MODE: Charger le gamelist.json existant et faire un merge intelligent
            File gamelistFile = new File(consoleDir, "gamelist.json");
            JSONArray existingGames = new JSONArray();
            java.util.HashSet<String> existingPaths = new java.util.HashSet<>();
            
            // Charger les entrées existantes
            if (gamelistFile.exists()) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(gamelistFile);
                    byte[] buffer = new byte[(int) gamelistFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String existingJson = new String(buffer, "UTF-8");
                    existingGames = new JSONArray(existingJson);
                    
                    // Compter les entrées existantes
                    for (int i = 0; i < existingGames.length(); i++) {
                        try {
                            JSONObject game = existingGames.getJSONObject(i);
                            existingPaths.add(game.getString("path"));
                        } catch (Exception e) {
                            // Ignorer
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("ConsoleManager", "Could not load existing gamelist.json, creating new one");
                }
            }
            
            // Créer une map des ROMs existantes (path -> JSONObject)
            java.util.HashMap<String, JSONObject> existingRomsMap = new java.util.HashMap<>();
            for (int i = 0; i < existingGames.length(); i++) {
                try {
                    JSONObject game = existingGames.getJSONObject(i);
                    String path = game.getString("path");
                    existingRomsMap.put(path, game);
                } catch (Exception e) {
                    // Ignorer les entrées invalides
                }
            }
            
            // Créer le nouveau JSON en fusionnant les données
            JSONArray gamesArray = new JSONArray();
            int newId = 1;
            
            for (ScannedRom rom : scannedRoms) {
                JSONObject gameObj;
                
                // Vérifier si ce ROM existe déjà
                if (existingRomsMap.containsKey(rom.path)) {
                    // GARDER les métadonnées existantes
                    gameObj = existingRomsMap.get(rom.path);
                    // Mettre à jour seulement l'ID pour la cohérence
                    gameObj.put("id", String.valueOf(newId++));
                    
                    // Mettre à jour l'image si elle existe maintenant
                    if (rom.hasBox2dImage && !gameObj.optString("image", "").contains("box2d")) {
                        gameObj.put("image", "./media/box2d/" + rom.name + ".png");
                        result.updatedRoms++;
                    }
                } else {
                    // NOUVEAU ROM: créer une entrée basique
                    gameObj = new JSONObject();
                    gameObj.put("id", String.valueOf(newId++));
                    gameObj.put("name", rom.name);
                    gameObj.put("path", rom.path);
                    gameObj.put("image", rom.hasBox2dImage ? "./media/box2d/" + rom.name + ".png" : "");
                    gameObj.put("desc", "");
                    gameObj.put("releasedate", "");
                    gameObj.put("developer", "");
                    gameObj.put("publisher", "");
                    gameObj.put("genre", "");
                    gameObj.put("players", "");
                    result.newRoms++;
                }
                
                // Compter les images manquantes
                if (!rom.hasBox2dImage && !rom.hasScreenshot) {
                    result.missingImages++;
                }
                
                gamesArray.put(gameObj);
            }
            
            // Calculer les ROMs supprimés (dans l'ancien JSON mais plus sur le disque)
            result.removedRoms = existingPaths.size() - (result.totalRoms - result.newRoms);
            if (result.removedRoms < 0) result.removedRoms = 0;
            
            // Écrire le nouveau gamelist.json au format {"games": [...]}
            JSONObject gamelistWrapper = new JSONObject();
            gamelistWrapper.put("games", gamesArray);
            java.io.FileWriter writer = new java.io.FileWriter(gamelistFile);
            writer.write(gamelistWrapper.toString(2));
            writer.close();
            
            result.success = true;
            return result;
        } catch (Exception e) {
            android.util.Log.e("ConsoleManager", "Error in scanConsoleWithAudit: " + e.getMessage());
            result.success = false;
            return result;
        }
    }
    
    private boolean scanConsoleSilently(String consoleId, String extensionsStr) {
        try {
            File consoleDir = new File(GAMELIBRARY_DIR + "/" + consoleId);
            if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                return false;
            }
            
            // Charger les extensions depuis console.json si présent
            List<String> extensions = new ArrayList<>();
            File consoleJsonFile = new File(consoleDir, "console.json");
            if (consoleJsonFile.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(consoleJsonFile);
                byte[] buffer = new byte[(int) consoleJsonFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer, "UTF-8");
                JSONObject consoleJson = new JSONObject(json);
                
                if (consoleJson.has("extensions")) {
                    JSONArray extArray = consoleJson.getJSONArray("extensions");
                    for (int i = 0; i < extArray.length(); i++) {
                        extensions.add(extArray.getString(i).toLowerCase());
                    }
                }
            }
            
            // Si pas d'extensions dans console.json, utiliser celles fournies
            if (extensions.isEmpty() && !extensionsStr.isEmpty()) {
                String[] parts = extensionsStr.split(",");
                for (String ext : parts) {
                    String cleaned = ext.trim().toLowerCase();
                    if (!cleaned.isEmpty()) {
                        if (!cleaned.startsWith(".")) {
                            cleaned = "." + cleaned;
                        }
                        extensions.add(cleaned);
                    }
                }
            }
            
            // Scanner les ROMs dans le répertoire
            File[] files = consoleDir.listFiles();
            List<ScannedRom> scannedRoms = new ArrayList<>();
            int romCounter = 1;
            
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String fileName = file.getName();
                        
                        // Vérifier si c'est un ROM
                        boolean isRom = false;
                        if (!extensions.isEmpty()) {
                            for (String ext : extensions) {
                                if (fileName.toLowerCase().endsWith(ext)) {
                                    isRom = true;
                                    break;
                                }
                            }
                        } else {
                            isRom = isRomFile(fileName);
                        }
                        
                        if (isRom) {
                            String baseName = getBaseNameFromFile(fileName);
                            
                            // Vérifier si les images existent
                            File box2dImage = new File(consoleDir, "media/box2d/" + baseName + ".png");
                            File screenshotImage = new File(consoleDir, "media/screenshots/" + baseName + ".png");
                            
                            ScannedRom rom = new ScannedRom();
                            rom.id = String.valueOf(romCounter++);
                            rom.name = baseName;
                            rom.path = "./" + fileName;
                            rom.hasBox2dImage = box2dImage.exists();
                            rom.hasScreenshot = screenshotImage.exists();
                            
                            scannedRoms.add(rom);
                        }
                    }
                }
            }
            
            // Si aucun ROM trouvé, ignorer
            if (scannedRoms.isEmpty()) {
                return false;
            }
            
            // AUDIT MODE: Charger le gamelist.json existant et faire un merge intelligent
            File gamelistFile = new File(consoleDir, "gamelist.json");
            JSONArray existingGames = new JSONArray();
            
            // Charger les entrées existantes
            if (gamelistFile.exists()) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(gamelistFile);
                    byte[] buffer = new byte[(int) gamelistFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String existingJson = new String(buffer, "UTF-8");
                    JSONObject gamelistObj = new JSONObject(existingJson);
                    existingGames = gamelistObj.getJSONArray("games");
                } catch (Exception e) {
                    android.util.Log.w("ConsoleManager", "Could not load existing gamelist.json, creating new one");
                }
            }
            
            // Créer une map des ROMs existantes (path -> JSONObject)
            java.util.HashMap<String, JSONObject> existingRomsMap = new java.util.HashMap<>();
            for (int i = 0; i < existingGames.length(); i++) {
                try {
                    JSONObject game = existingGames.getJSONObject(i);
                    String path = game.getString("path");
                    existingRomsMap.put(path, game);
                } catch (Exception e) {
                    // Ignorer les entrées invalides
                }
            }
            
            // Créer le nouveau JSON en fusionnant les données
            JSONArray gamesArray = new JSONArray();
            int newId = 1;
            
            for (ScannedRom rom : scannedRoms) {
                JSONObject gameObj;
                
                // Vérifier si ce ROM existe déjà
                if (existingRomsMap.containsKey(rom.path)) {
                    // GARDER les métadonnées existantes
                    gameObj = existingRomsMap.get(rom.path);
                    // Mettre à jour seulement l'ID pour la cohérence
                    gameObj.put("id", String.valueOf(newId++));
                    
                    // Mettre à jour l'image si elle existe maintenant
                    if (rom.hasBox2dImage && !gameObj.optString("image", "").contains("box2d")) {
                        gameObj.put("image", "./media/box2d/" + rom.name + ".png");
                    }
                } else {
                    // NOUVEAU ROM: créer une entrée basique
                    gameObj = new JSONObject();
                    gameObj.put("id", String.valueOf(newId++));
                    gameObj.put("name", rom.name);
                    gameObj.put("path", rom.path);
                    gameObj.put("image", rom.hasBox2dImage ? "./media/box2d/" + rom.name + ".png" : "");
                    gameObj.put("desc", "");
                    gameObj.put("releasedate", "");
                    gameObj.put("developer", "");
                    gameObj.put("publisher", "");
                    gameObj.put("genre", "");
                    gameObj.put("players", "");
                }
                
                gamesArray.put(gameObj);
            }
            
            // Écrire le nouveau gamelist.json au format {"games": [...]}
            JSONObject gamelistWrapper = new JSONObject();
            gamelistWrapper.put("games", gamesArray);
            java.io.FileWriter writer = new java.io.FileWriter(gamelistFile);
            writer.write(gamelistWrapper.toString(2));
            writer.close();
            
            return true;
        } catch (Exception e) {
            android.util.Log.e("ConsoleManager", "Error in scanConsoleSilently: " + e.getMessage());
            return false;
        }
    }
    
    private void loadAvailableCores() {
        new Thread(() -> {
            try {
                // Lire cores.json pour obtenir la liste des cores disponibles
                File coresFile = new File(GAMELIBRARY_DIR + "/data/cores/cores.json");
                if (coresFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(coresFile);
                    byte[] buffer = new byte[(int) coresFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String json = new String(buffer, "UTF-8");
                    
                    JSONArray coresArray = new JSONArray(json);
                    List<String> tempCores = new ArrayList<>();
                    
                    // Ajouter "auto" en premier
                    tempCores.add("auto");
                    
                    for (int i = 0; i < coresArray.length(); i++) {
                        JSONObject core = coresArray.getJSONObject(i);
                        String coreName = core.getString("name");
                        tempCores.add(coreName);
                    }
                    
                    runOnUiThread(() -> {
                        availableCores = tempCores;
                        Log.i(TAG, "Loaded " + availableCores.size() + " available cores from cores.json");
                    });
                } else {
                    Log.w(TAG, "cores.json not found, using fallback cores");
                    runOnUiThread(() -> setFallbackCores());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cores.json", e);
                runOnUiThread(() -> setFallbackCores());
            }
        }).start();
    }
    
    private void setFallbackCores() {
        availableCores.clear();
        availableCores.add("auto");
        // Arcade cores
        availableCores.add("fbneo");
        availableCores.add("mame2010");
        availableCores.add("mame2003_plus");
        availableCores.add("mame2003");
        availableCores.add("fbalpha2012_cps1");
        availableCores.add("fbalpha2012_cps2");
        availableCores.add("flycast");
        // Console cores
        availableCores.add("fceumm");
        availableCores.add("nestopia");
        availableCores.add("snes9x");
        availableCores.add("parallel_n64");
        availableCores.add("mupen64plus_next");
        availableCores.add("mupen64plus_next_gles3");
        availableCores.add("mupen64plus_next_gles2");
        availableCores.add("mgba");
        availableCores.add("gambatte");
        availableCores.add("melonds");
        availableCores.add("desmume");
        availableCores.add("pcsx_rearmed");
        availableCores.add("genesis_plus_gx");
        availableCores.add("picodrive");
    }
    
    private void loadConsoles() {
        new Thread(() -> {
            try {
                // Scanner directement GameLibrary-Data pour toutes les consoles
                File gamelibraryDir = new File(GAMELIBRARY_DIR);
                List<ConsoleConfig> tempConsoles = new ArrayList<>();
                
                if (gamelibraryDir.exists() && gamelibraryDir.isDirectory()) {
                    File[] directories = gamelibraryDir.listFiles(File::isDirectory);
                    
                    if (directories != null) {
                        for (File dir : directories) {
                            String dirName = dir.getName();
                            
                            // Ignorer les répertoires système
                            if (dirName.equals("data") || dirName.equals("emulatorjs") || 
                                dirName.equals("vmnes") || dirName.equals("playlists") ||
                                dirName.equals("saves") || dirName.equals("states") ||
                                dirName.equals("cheats") || dirName.equals("media") || 
                                dirName.equals("overlays") || dirName.equals("cores") ||
                                dirName.equals("bios") || dirName.startsWith(".")) {
                                continue;
                            }
                            
                            // Scanner les sous-consoles (ex: fbneo/sega, fbneo/Taito) AVANT le parent
                            File[] subDirectories = dir.listFiles(File::isDirectory);
                            boolean hasSubconsoles = false;
                            
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
                                    
                                    // Vérifier s'il y a des ROMs ou un gamelist.json dans ce sous-répertoire
                                    File[] romFiles = subDir.listFiles(file -> {
                                        String name = file.getName().toLowerCase();
                                        return file.isFile() && (name.endsWith(".zip") || name.endsWith(".bin") || 
                                                                 name.endsWith(".iso") || name.endsWith(".chd"));
                                    });
                                    
                                    File subGamelistFile = new File(subDir, "gamelist.json");
                                    boolean hasRoms = romFiles != null && romFiles.length > 0;
                                    boolean hasGamelist = subGamelistFile.exists();
                                    
                                    if (hasRoms || hasGamelist) {
                                        // C'est une sous-console valide
                                        String subConsoleId = dirName + "/" + subDirName;
                                        ConsoleConfig subConfig = scanConsoleDirectory(subDir, subConsoleId);
                                        if (subConfig != null) {
                                            tempConsoles.add(subConfig);
                                            hasSubconsoles = true;
                                            Log.i(TAG, "Sub-console scannée: " + subConsoleId);
                                        }
                                    }
                                }
                            }
                            
                            // Scanner la console parent (même si elle a des sous-consoles)
                            ConsoleConfig parentConfig = scanConsoleDirectory(dir, dirName);
                            if (parentConfig != null) {
                                tempConsoles.add(parentConfig);
                                Log.i(TAG, "Console parent ajoutée: " + dirName + (hasSubconsoles ? " (avec sous-consoles)" : ""));
                            }
                        }
                    }
                }
                
                // Trier par nom (les sous-consoles apparaîtront après leur parent)
                tempConsoles.sort((a, b) -> a.id.compareToIgnoreCase(b.id));
                
                runOnUiThread(() -> {
                    consoles = tempConsoles;
                    adapter = new ConsoleAdapter(consoles);
                    recyclerView.setAdapter(adapter);
                    Log.i(TAG, "Loaded " + consoles.size() + " consoles from GameLibrary-Data");
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
     * Scanner un répertoire de console et créer la config
     */
    private ConsoleConfig scanConsoleDirectory(File dir, String consoleId) {
        try {
            ConsoleConfig config = new ConsoleConfig();
            config.id = consoleId;
            
            // Vérifier si gamelist.json existe
            File gamelistFile = new File(dir, "gamelist.json");
            config.hasGamelist = gamelistFile.exists();
            
            // Vérifier si console.json existe pour les métadonnées
            File consoleJsonFile = new File(dir, "console.json");
            if (consoleJsonFile.exists()) {
                // Charger depuis console.json
                java.io.FileInputStream fis = new java.io.FileInputStream(consoleJsonFile);
                byte[] buffer = new byte[(int) consoleJsonFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer, "UTF-8");
                
                JSONObject obj = new JSONObject(json);
                config.name = obj.optString("name", consoleId.toUpperCase());
                config.fullName = obj.optString("fullName", getDefaultFullName(consoleId));
                config.defaultCore = obj.optString("defaultCore", "auto");
                config.color = obj.optString("color", "#FF0000");
                config.isGeneric = obj.optBoolean("isGeneric", false);
                
                // Parse cores et extensions
                config.cores = new ArrayList<>();
                config.extensions = new ArrayList<>();
                
                JSONArray coresArray = obj.optJSONArray("cores");
                if (coresArray != null) {
                    for (int j = 0; j < coresArray.length(); j++) {
                        config.cores.add(coresArray.getString(j));
                    }
                }
                
                JSONArray extsArray = obj.optJSONArray("extensions");
                if (extsArray != null) {
                    for (int j = 0; j < extsArray.length(); j++) {
                        config.extensions.add(extsArray.getString(j));
                    }
                }
            } else {
                // Pas de console.json - utiliser des valeurs par défaut
                String baseName = consoleId.contains("/") ? consoleId.substring(consoleId.lastIndexOf("/") + 1) : consoleId;
                config.name = baseName.toUpperCase();
                config.fullName = getDefaultFullName(baseName);
                config.defaultCore = "fbneo"; // Par défaut pour les sous-consoles
                config.color = "#FF0000";
                config.isGeneric = false;
                config.cores = new ArrayList<>();
                config.extensions = new ArrayList<>();
                config.extensions.add(".zip");
            }
            
            // Marquer comme utilisant le scanner auto si pas de gamelist.json
            config.usesAutoScan = !config.hasGamelist;
            
            Log.i(TAG, "Console scannée: " + consoleId + 
                (config.hasGamelist ? " (avec gamelist.json)" : " (AUTO SCAN)"));
            
            return config;
            
        } catch (Exception e) {
            Log.e(TAG, "Error scanning console directory: " + consoleId, e);
            return null;
        }
    }
    
    /**
     * Retourne le nom complet par défaut pour une console
     */
    private String getDefaultFullName(String consoleId) {
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes": case "famicom": return "Nintendo Entertainment System";
            case "snes": case "superfamicom": return "Super Nintendo Entertainment System";
            case "n64": return "Nintendo 64";
            case "gb": return "Game Boy";
            case "gbc": return "Game Boy Color";
            case "gba": return "Game Boy Advance";
            case "nds": case "ds": return "Nintendo DS";
            case "3ds": return "Nintendo 3DS";
            case "virtualboy": case "vb": return "Virtual Boy";
            
            // Sega
            case "megadrive": case "genesis": case "md": return "Sega Genesis / Mega Drive";
            case "mastersystem": case "sms": return "Sega Master System";
            case "gamegear": case "gg": return "Sega Game Gear";
            case "saturn": return "Sega Saturn";
            case "dreamcast": case "dc": return "Sega Dreamcast";
            case "32x": return "Sega 32X";
            case "segacd": case "megacd": return "Sega CD / Mega CD";
            
            // Sony
            case "ps1": case "psx": case "playstation": return "Sony PlayStation";
            case "ps2": return "Sony PlayStation 2";
            case "psp": return "PlayStation Portable";
            
            // Atari
            case "atari2600": case "a2600": return "Atari 2600";
            case "atari5200": case "a5200": return "Atari 5200";
            case "atari7800": case "a7800": return "Atari 7800";
            case "atarist": case "st": return "Atari ST";
            case "lynx": return "Atari Lynx";
            case "jaguar": return "Atari Jaguar";
            
            // Other
            case "pcengine": case "pce": case "tg16": return "PC Engine / TurboGrafx-16";
            case "neogeo": case "ngp": return "Neo Geo Pocket";
            case "wonderswan": case "ws": return "WonderSwan";
            case "vectrex": return "Vectrex";
            case "intellivision": return "Intellivision";
            case "colecovision": return "ColecoVision";
            case "c64": case "commodore64": return "Commodore 64";
            case "amiga": return "Commodore Amiga";
            case "msx": case "msx2": return "MSX / MSX2";
            case "cpc": case "amstrad": return "Amstrad CPC";
            case "pokemonmini": return "Pokemon Mini";
            case "arcade": case "mame": return "Arcade / MAME";
            
            default: return consoleId.toUpperCase() + " (Custom Console)";
        }
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
                android.widget.Toast.makeText(this, "Console ID is required to scan", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Lancer le scan
            scanRomsAndGenerateGamelist(id, extensions);
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
                android.widget.Toast.makeText(this, "ID and Name are required", android.widget.Toast.LENGTH_SHORT).show();
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
                // Vérifier si le répertoire de la console existe
                File consoleDir = new File(GAMELIBRARY_DIR + "/" + id);
                
                if (!consoleDir.exists()) {
                    runOnUiThread(() -> {
                        // Demander à l'utilisateur s'il veut créer le répertoire
                        new AlertDialog.Builder(this)
                            .setTitle("Directory not found")
                            .setMessage("The directory '" + id + "/' does not exist.\n\n" +
                                       "Path: " + consoleDir.getAbsolutePath() + "\n\n" +
                                       "Do you want to create it?")
                            .setPositiveButton("CREATE", (dialog, which) -> {
                                createConsoleDirectory(consoleDir, id, name, fullName, defaultCore, extensions, color);
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
                String filePath = GAMELIBRARY_DIR + "/" + id + "/console.json";
                FileOutputStream fos = new FileOutputStream(filePath);
                fos.write(config.toString(2).getBytes("UTF-8"));
                fos.close();
                
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Console configuration saved: " + id, android.widget.Toast.LENGTH_SHORT).show();
                    loadConsoles(); // Reload list
                });
                
                Log.i(TAG, "Console config saved: " + filePath);
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving console config", e);
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
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
     * Scanner le répertoire d'une console et générer un gamelist.json
     */
    private void scanRomsAndGenerateGamelist(String consoleId, String extensionsStr) {
        // Afficher un dialog de chargement
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Scanning ROMs...");
        progressDialog.setMessage("Please wait while scanning directory...");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                // Vérifier que le répertoire existe
                File consoleDir = new File(GAMELIBRARY_DIR + "/" + consoleId);
                if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        android.widget.Toast.makeText(this, 
                            "Console directory not found: " + consoleDir.getAbsolutePath(), 
                            android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                // Charger les extensions depuis console.json si présent
                List<String> extensions = new ArrayList<>();
                File consoleJsonFile = new File(consoleDir, "console.json");
                if (consoleJsonFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(consoleJsonFile);
                    byte[] buffer = new byte[(int) consoleJsonFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String json = new String(buffer, "UTF-8");
                    JSONObject consoleJson = new JSONObject(json);
                    
                    if (consoleJson.has("extensions")) {
                        JSONArray extArray = consoleJson.getJSONArray("extensions");
                        for (int i = 0; i < extArray.length(); i++) {
                            extensions.add(extArray.getString(i).toLowerCase());
                        }
                    }
                }
                
                // Si pas d'extensions dans console.json, utiliser celles fournies
                if (extensions.isEmpty() && !extensionsStr.isEmpty()) {
                    String[] parts = extensionsStr.split(",");
                    for (String ext : parts) {
                        String cleaned = ext.trim().toLowerCase();
                        if (!cleaned.isEmpty()) {
                            if (!cleaned.startsWith(".")) {
                                cleaned = "." + cleaned;
                            }
                            extensions.add(cleaned);
                        }
                    }
                }
                
                // Si toujours pas d'extensions, utiliser les extensions par défaut de RomScanner
                if (extensions.isEmpty()) {
                    Log.i(TAG, "No extensions specified, using default ROM extensions");
                }
                
                // Scanner le répertoire pour les ROMs
                File[] files = consoleDir.listFiles();
                List<ScannedRom> scannedRoms = new ArrayList<>();
                int romCounter = 1;
                
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            String fileName = file.getName();
                            
                            // Vérifier si c'est un ROM
                            boolean isRom = false;
                            if (!extensions.isEmpty()) {
                                for (String ext : extensions) {
                                    if (fileName.toLowerCase().endsWith(ext)) {
                                        isRom = true;
                                        break;
                                    }
                                }
                            } else {
                                // Utiliser la détection par défaut de RomScanner
                                isRom = isRomFile(fileName);
                            }
                            
                            if (isRom) {
                                String baseName = getBaseNameFromFile(fileName);
                                
                                // Vérifier si les images existent
                                File box2dImage = new File(consoleDir, "media/box2d/" + baseName + ".png");
                                File screenshotImage = new File(consoleDir, "media/screenshots/" + baseName + ".png");
                                
                                ScannedRom rom = new ScannedRom();
                                rom.id = String.valueOf(romCounter++);
                                rom.name = baseName;
                                rom.path = "./" + fileName;
                                rom.hasBox2dImage = box2dImage.exists();
                                rom.hasScreenshot = screenshotImage.exists();
                                
                                scannedRoms.add(rom);
                            }
                        }
                    }
                }
                
                final int romsFound = scannedRoms.size();
                final List<ScannedRom> finalRoms = scannedRoms;
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (romsFound == 0) {
                        android.widget.Toast.makeText(this, 
                            "No ROMs found in directory", 
                            android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    // Afficher le dialog de preview
                    showGamelistPreviewDialog(consoleId, finalRoms);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error scanning ROMs", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Error scanning: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * Afficher un dialog de preview avec les ROMs trouvés et options Merge/Replace
     */
    private void showGamelistPreviewDialog(String consoleId, List<ScannedRom> roms) {
        // Construire le message de preview
        StringBuilder message = new StringBuilder();
        message.append("Found ").append(roms.size()).append(" ROM(s) in directory:\n\n");
        
        int maxPreview = Math.min(10, roms.size());
        for (int i = 0; i < maxPreview; i++) {
            ScannedRom rom = roms.get(i);
            message.append("- ").append(rom.name);
            if (rom.hasBox2dImage || rom.hasScreenshot) {
                message.append(" [");
                if (rom.hasBox2dImage) message.append("BOX");
                if (rom.hasBox2dImage && rom.hasScreenshot) message.append(", ");
                if (rom.hasScreenshot) message.append("SCREEN");
                message.append("]");
            }
            message.append("\n");
        }
        
        if (roms.size() > maxPreview) {
            message.append("... and ").append(roms.size() - maxPreview).append(" more\n");
        }
        
        message.append("\n\nChoose an option:");
        
        // Vérifier si un gamelist.json existe déjà
        File gamelistFile = new File(GAMELIBRARY_DIR + "/" + consoleId + "/gamelist.json");
        boolean gamelistExists = gamelistFile.exists();
        
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
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Saving gamelist.json...");
        progressDialog.setMessage("Please wait...");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                File gamelistFile = new File(GAMELIBRARY_DIR + "/" + consoleId + "/gamelist.json");
                
                JSONArray gamesArray = new JSONArray();
                
                // Si merge, charger l'existant d'abord
                if (merge && gamelistFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(gamelistFile);
                    byte[] buffer = new byte[(int) gamelistFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String json = new String(buffer, "UTF-8");
                    
                    JSONObject existingGamelist = new JSONObject(json);
                    if (existingGamelist.has("games")) {
                        gamesArray = existingGamelist.getJSONArray("games");
                    }
                }
                
                // Ajouter les nouveaux ROMs
                for (ScannedRom rom : roms) {
                    // Vérifier si le ROM existe déjà (si merge)
                    boolean exists = false;
                    if (merge) {
                        for (int i = 0; i < gamesArray.length(); i++) {
                            JSONObject game = gamesArray.getJSONObject(i);
                            if (game.getString("path").equals(rom.path)) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    
                    if (!exists) {
                        JSONObject game = new JSONObject();
                        game.put("id", rom.id);
                        game.put("name", rom.name);
                        game.put("path", rom.path);
                        game.put("desc", "Custom ROM - No description available");
                        game.put("image", rom.hasBox2dImage ? 
                            "./media/box2d/" + rom.name + ".png" : "./media/box2d/fallback.png");
                        game.put("screenshot", rom.hasScreenshot ? 
                            "./media/screenshots/" + rom.name + ".png" : "./media/screenshots/fallback.png");
                        game.put("thumbnail", rom.hasBox2dImage ? 
                            "./media/box2d/" + rom.name + ".png" : "./media/box2d/fallback.png");
                        game.put("releasedate", "Unknown");
                        game.put("genre", "Custom");
                        game.put("players", "1-2");
                        
                        gamesArray.put(game);
                    }
                }
                
                // Créer le gamelist final
                JSONObject gamelist = new JSONObject();
                gamelist.put("games", gamesArray);
                
                // Sauvegarder
                FileOutputStream fos = new FileOutputStream(gamelistFile);
                fos.write(gamelist.toString(2).getBytes("UTF-8"));
                fos.close();
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Gamelist saved successfully!\n" + roms.size() + " ROMs added", 
                        android.widget.Toast.LENGTH_LONG).show();
                    
                    // Recharger la liste des consoles
                    loadConsoles();
                });
                
                Log.i(TAG, "Gamelist saved: " + consoleId + " with " + roms.size() + " ROMs");
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving gamelist", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Error saving gamelist: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * Vérifier si un fichier est un ROM (utilise la logique de RomScanner)
     */
    private boolean isRomFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".nes") || lowerName.endsWith(".smc") || lowerName.endsWith(".sfc") ||
               lowerName.endsWith(".z64") || lowerName.endsWith(".n64") || lowerName.endsWith(".v64") ||
               lowerName.endsWith(".bin") || lowerName.endsWith(".md") || lowerName.endsWith(".gen") ||
               lowerName.endsWith(".smd") || lowerName.endsWith(".gba") || lowerName.endsWith(".gb") ||
               lowerName.endsWith(".gbc") || lowerName.endsWith(".nds") || lowerName.endsWith(".pbp") ||
               lowerName.endsWith(".iso") || lowerName.endsWith(".cue") || lowerName.endsWith(".img") ||
               lowerName.endsWith(".cso") || lowerName.endsWith(".zip") || lowerName.endsWith(".7z") ||
               lowerName.endsWith(".rar") || lowerName.endsWith(".chd");
    }
    
    /**
     * Extraire le nom de base d'un fichier ROM (sans extension)
     */
    private String getBaseNameFromFile(String fileName) {
        String baseName = fileName;
        
        // Enlever les extensions connues
        String[] extensions = {".nes", ".smc", ".sfc", ".z64", ".n64", ".v64", ".bin", ".md", 
                               ".gen", ".smd", ".gba", ".gb", ".gbc", ".nds", ".pbp", ".iso", 
                               ".cue", ".img", ".cso", ".zip", ".7z", ".rar", ".chd"};
        
        for (String ext : extensions) {
            if (baseName.toLowerCase().endsWith(ext)) {
                baseName = baseName.substring(0, baseName.length() - ext.length());
                break;
            }
        }
        
        return baseName;
    }
    
    /**
     * Classe pour stocker les infos d'un ROM scanné
     */
    private static class ScannedRom {
        String id;
        String name;
        String path;
        boolean hasBox2dImage;
        boolean hasScreenshot;
    }
    
    // Console config data class
    static class ConsoleConfig {
        String id;
        String name;
        String fullName;
        String defaultCore;
        String color;
        boolean isGeneric;
        boolean hasGamelist;  // Indique si gamelist.json existe
        boolean usesAutoScan; // Indique si le scanner automatique est utilisé
        List<String> cores;
        List<String> extensions;
    }
    
    static class AuditResult {
        String consoleId;
        String consoleName;
        boolean success;
        int totalRoms;
        int newRoms;
        int updatedRoms;
        int removedRoms;
        int missingImages;
        
        AuditResult() {
            this.success = false;
            this.totalRoms = 0;
            this.newRoms = 0;
            this.updatedRoms = 0;
            this.removedRoms = 0;
            this.missingImages = 0;
        }
    }
    
    // RecyclerView Adapter
    class ConsoleAdapter extends RecyclerView.Adapter<ConsoleAdapter.ViewHolder> {
        
        private List<ConsoleConfig> consoles;
        
        ConsoleAdapter(List<ConsoleConfig> consoles) {
            this.consoles = consoles;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_console_manager, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConsoleConfig console = consoles.get(position);
            
            // Vérifier si c'est une sous-console (contient "/")
            boolean isSubConsole = console.id.contains("/");
            
            if (isSubConsole) {
                // Indenter visuellement les sous-consoles
                holder.consoleName.setText("  └─ " + console.name);
                holder.consoleFullName.setText("     " + console.fullName);
            } else {
                holder.consoleName.setText(console.name);
                holder.consoleFullName.setText(console.fullName);
            }
            
            holder.consoleCore.setText("Core: " + console.defaultCore);
            
            // Afficher le badge approprié
            if (console.usesAutoScan) {
                // Console sans gamelist.json - utilise le scanner automatique
                holder.autoScanBadge.setVisibility(View.VISIBLE);
                holder.genericBadge.setVisibility(View.GONE);
            } else if (console.isGeneric) {
                // Console générique
                holder.genericBadge.setVisibility(View.VISIBLE);
                holder.autoScanBadge.setVisibility(View.GONE);
            } else {
                // Console standard avec gamelist.json
                holder.genericBadge.setVisibility(View.GONE);
                holder.autoScanBadge.setVisibility(View.GONE);
            }
            
            holder.editButton.setOnClickListener(v -> showEditConsoleDialog(console));
            
            // Bouton refresh - scanner/regénérer le gamelist.json
            holder.refreshButton.setOnClickListener(v -> {
                String extensions = String.join(", ", console.extensions);
                scanRomsAndGenerateGamelist(console.id, extensions);
            });
        }
        
        @Override
        public int getItemCount() {
            return consoles.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView consoleIcon;
            TextView consoleName;
            TextView consoleFullName;
            TextView consoleCore;
            TextView genericBadge;
            TextView autoScanBadge;
            TextView editButton;
            TextView refreshButton;
            
            ViewHolder(View itemView) {
                super(itemView);
                consoleIcon = itemView.findViewById(R.id.consoleIcon);
                consoleName = itemView.findViewById(R.id.consoleName);
                consoleFullName = itemView.findViewById(R.id.consoleFullName);
                consoleCore = itemView.findViewById(R.id.consoleCore);
                genericBadge = itemView.findViewById(R.id.consoleGenericBadge);
                autoScanBadge = itemView.findViewById(R.id.consoleAutoScanBadge);
                editButton = itemView.findViewById(R.id.editButton);
                refreshButton = itemView.findViewById(R.id.refreshButton);
            }
        }
    }
    
    /**
     * Installer la base de données de cheats depuis les assets
     */
    private void installCheatsDatabase() {
        // Vérifier si le ZIP est déjà copié (mode lecture à la volée)
        File cheatsZip = new File("/storage/emulated/0/GameLibrary-Data/cheats.zip");
        
        // Vérifier si les cheats sont déjà extraits (mode extraction)
        File cheatsDir = new File("/storage/emulated/0/GameLibrary-Data/cheats");
        boolean hasExtractedCheats = cheatsDir.exists() && cheatsDir.listFiles() != null && cheatsDir.listFiles().length > 10;
        
        if (cheatsZip.exists() || hasExtractedCheats) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Cheats Already Installed");
            String message = cheatsZip.exists() ? 
                "Cheats ZIP is already installed (on-the-fly mode).\n\nDo you want to reinstall?" :
                "Cheats are already extracted.\n\nDo you want to reinstall?";
            builder.setMessage(message);
            builder.setPositiveButton("REINSTALL", (dialog, which) -> showCheatsInstallOptions());
            builder.setNegativeButton("CANCEL", null);
            builder.show();
            return;
        }
        
        // Première installation - Afficher les options
        showCheatsInstallOptions();
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
            copyZipToDevice();
        });
        
        builder.setNeutralButton("EXTRACT ALL", (dialog, which) -> {
            performCheatsInstallation();
        });
        
        builder.setNegativeButton("BY SYSTEM", (dialog, which) -> {
            showSystemSelector();
        });
        
        builder.show();
    }
    
    /**
     * Copier cheats.zip sur le device (mode ZIP, instantané)
     */
    private void copyZipToDevice() {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Installing Cheats");
        progressDialog.setMessage("Copying cheats.zip...");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                java.io.InputStream is = getAssets().open("cheats.zip");
                File destFile = new File("/storage/emulated/0/GameLibrary-Data/cheats.zip");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                
                fos.close();
                is.close();
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Cheats ready (ZIP mode)!", 
                        android.widget.Toast.LENGTH_LONG).show();
                });
                
                Log.i(TAG, "Cheats ZIP copied to device");
                
            } catch (Exception e) {
                Log.e(TAG, "Error copying cheats ZIP", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Error: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * Afficher le sélecteur de systèmes pour extraction partielle
     */
    private void showSystemSelector() {
        String[] systems = {
            "MAME 2003", "MAME 2003 Plus", "MAME 2010", "MAME 2015", "MAME 2016", "FBNeo",
            "NES", "SNES", "N64", "GB", "GBC", "GBA",
            "Genesis", "Master System", "Game Gear", "Sega CD", "32X",
            "PSX", "PSP",
            "Atari 2600", "Atari 5200", "Atari 7800", "Lynx",
            "Neo Geo", "WonderSwan", "PC Engine", "Virtual Boy"
        };
        
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
                if (selected) count++;
            }
            
            if (count == 0) {
                android.widget.Toast.makeText(this, "No systems selected", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Construire la liste des systèmes sélectionnés
            java.util.List<String> selectedSystemsList = new java.util.ArrayList<>();
            for (int i = 0; i < systems.length; i++) {
                if (selectedSystems[i]) {
                    selectedSystemsList.add(getSystemFolderName(systems[i]));
                }
            }
            
            extractSelectedSystems(selectedSystemsList);
        });
        
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }
    
    /**
     * Mapper le nom d'affichage au nom de dossier
     */
    private String getSystemFolderName(String displayName) {
        switch (displayName) {
            // Arcade systems
            case "MAME 2003": return "mame2003";
            case "MAME 2003 Plus": return "mame2003_plus";
            case "MAME 2010": return "mame2010";
            case "MAME 2015": return "mame2015";
            case "MAME 2016": return "mame2016";
            case "FBNeo": return "fbneo";
            
            // Console systems
            case "NES": return "nes";
            case "SNES": return "snes";
            case "N64": return "n64";
            case "GB": return "gb";
            case "GBC": return "gbc";
            case "GBA": return "gba";
            case "Genesis": return "genesis";
            case "Master System": return "mastersystem";
            case "Game Gear": return "gamegear";
            case "Sega CD": return "segacd";
            case "32X": return "32x";
            case "PSX": return "psx";
            case "PSP": return "psp";
            case "Atari 2600": return "atari2600";
            case "Atari 5200": return "atari5200";
            case "Atari 7800": return "atari7800";
            case "Lynx": return "atarilynx";
            case "Neo Geo": return "neogeo";
            case "WonderSwan": return "wonderswan";
            case "PC Engine": return "pce";
            case "Virtual Boy": return "virtualboy";
            default: return displayName.toLowerCase();
        }
    }
    
    /**
     * Extraire seulement les systèmes sélectionnés depuis le ZIP
     */
    private void extractSelectedSystems(java.util.List<String> systems) {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Installing Cheats");
        progressDialog.setMessage("Extracting selected systems...");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(systems.size());
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                String destPath = "/storage/emulated/0/GameLibrary-Data/cheats";
                File destDir = new File(destPath);
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }
                
                java.io.InputStream assetStream = getAssets().open("cheats.zip");
                java.util.zip.ZipInputStream zipStream = new java.util.zip.ZipInputStream(assetStream);
                
                int filesExtracted = 0;
                int currentSystemIndex = 0;
                String lastSystem = "";
                java.util.zip.ZipEntry entry;
                
                while ((entry = zipStream.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    
                    // Vérifier si cette entrée appartient à un système sélectionné
                    boolean shouldExtract = false;
                    String currentSystem = "";
                    
                    for (String system : systems) {
                        if (entryName.startsWith("retroarch/" + system + "/")) {
                            shouldExtract = true;
                            currentSystem = system;
                            break;
                        }
                    }
                    
                    // Extraire aussi les overrides et user
                    if (entryName.startsWith("retroarch/overrides/") || entryName.startsWith("user/")) {
                        shouldExtract = true;
                    }
                    
                    if (shouldExtract) {
                        // Détecter changement de système
                        if (!currentSystem.equals(lastSystem) && !currentSystem.isEmpty()) {
                            lastSystem = currentSystem;
                            currentSystemIndex++;
                            final String systemName = currentSystem;
                            final int sysIdx = currentSystemIndex;
                            runOnUiThread(() -> {
                                progressDialog.setProgress(sysIdx);
                                progressDialog.setMessage("Extracting " + systemName.toUpperCase() + "...");
                            });
                        }
                        
                        File outputFile = new File(destDir, entryName);
                        
                        if (entry.isDirectory()) {
                            outputFile.mkdirs();
                        } else {
                            outputFile.getParentFile().mkdirs();
                            
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
                            byte[] buffer = new byte[65536];
                            int bytesRead;
                            while ((bytesRead = zipStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                            fos.close();
                            filesExtracted++;
                        }
                    }
                    
                    zipStream.closeEntry();
                }
                
                zipStream.close();
                assetStream.close();
                
                final int totalFiles = filesExtracted;
                runOnUiThread(() -> {
                    progressDialog.setProgress(systems.size());
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Cheats installed: " + totalFiles + " files (" + systems.size() + " systems)", 
                        android.widget.Toast.LENGTH_LONG).show();
                });
                
                Log.i(TAG, "Selected systems cheats extracted: " + totalFiles + " files");
                
            } catch (Exception e) {
                Log.e(TAG, "Error extracting selected systems", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Error: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * Effectuer l'installation des cheats
     */
    private void performCheatsInstallation() {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Installing Cheats");
        progressDialog.setMessage("Preparing...");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                // Vérifier si cheats.zip existe dans les assets
                boolean zipExists = false;
                try {
                    getAssets().open("cheats.zip").close();
                    zipExists = true;
                    Log.i(TAG, "✅ Found cheats.zip in assets, using fast extraction (ZIP method)");
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(this, "Using ZIP extraction (fast)", android.widget.Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ cheats.zip not found in assets, using folder copy method (slow)");
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(this, "Using folder copy (slow)", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
                
                if (zipExists) {
                    // Méthode rapide : Extraire le ZIP
                    extractCheatsZip(progressDialog);
                } else {
                    // Méthode lente : Copier fichier par fichier (fallback)
                    final int[] fileCount = {0};
                    final String[] currentSystem = {""};
                    
                    copyAssetFolderWithSystemCallback("GameLibrary-Data/cheats", "/storage/emulated/0/GameLibrary-Data/cheats", 
                        new SystemProgressCallback() {
                            @Override
                            public void onSystemStart(String systemName, int systemIndex, int totalSystems) {
                                currentSystem[0] = systemName;
                                runOnUiThread(() -> {
                                    progressDialog.setMessage("Installing cheats...\n(" + systemIndex + "/" + totalSystems + ") " + systemName.toUpperCase());
                                    int progress = (systemIndex * 100) / totalSystems;
                                    progressDialog.setProgress(progress);
                                });
                            }
                            
                            @Override
                            public void onFileProgress(String fileName) {
                                fileCount[0]++;
                            }
                        });
                    
                    runOnUiThread(() -> {
                        progressDialog.setProgress(100);
                        progressDialog.dismiss();
                        android.widget.Toast.makeText(this, 
                            "Cheats installed: " + fileCount[0] + " files", 
                            android.widget.Toast.LENGTH_LONG).show();
                    });
                    
                    Log.i(TAG, "Cheats database installed: " + fileCount[0] + " files");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error installing cheats", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.widget.Toast.makeText(this, 
                        "Error installing cheats: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * Extraire cheats.zip depuis les assets (méthode rapide)
     */
    private void extractCheatsZip(android.app.ProgressDialog progressDialog) throws Exception {
        String destPath = "/storage/emulated/0/GameLibrary-Data/cheats";
        File destDir = new File(destPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        runOnUiThread(() -> {
            progressDialog.setMessage("Extracting cheats.zip...");
            progressDialog.setProgress(10);
        });
        
        java.io.InputStream assetStream = getAssets().open("cheats.zip");
        java.util.zip.ZipInputStream zipStream = new java.util.zip.ZipInputStream(assetStream);
        
        int fileCount = 0;
        String currentSystem = "";
        java.util.zip.ZipEntry entry;
        
        while ((entry = zipStream.getNextEntry()) != null) {
            String entryName = entry.getName();
            File outputFile = new File(destDir, entryName);
            
            if (entry.isDirectory()) {
                outputFile.mkdirs();
                
                // Détecter les changements de système (ex: retroarch/nes/, retroarch/snes/)
                if (entryName.startsWith("retroarch/") && entryName.endsWith("/")) {
                    String[] parts = entryName.split("/");
                    if (parts.length >= 2) {
                        String newSystem = parts[1];
                        if (!newSystem.equals(currentSystem) && !newSystem.equals("overrides")) {
                            currentSystem = newSystem;
                            final String systemName = currentSystem;
                            runOnUiThread(() -> {
                                progressDialog.setMessage("Extracting cheats...\n" + systemName.toUpperCase());
                            });
                        }
                    }
                }
            } else {
                // Créer les répertoires parents si nécessaires
                outputFile.getParentFile().mkdirs();
                
                // Extraire le fichier avec buffer 64KB pour performance
                java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;
                while ((bytesRead = zipStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                fileCount++;
                
                // Mettre à jour la progression tous les 1000 fichiers (moins fréquent = plus rapide)
                if (fileCount % 1000 == 0) {
                    final int count = fileCount;
                    runOnUiThread(() -> {
                        // Progression approximative (environ 10,000 fichiers total)
                        int progress = Math.min(90, 10 + (count / 110));
                        progressDialog.setProgress(progress);
                    });
                }
            }
            
            zipStream.closeEntry();
        }
        
        zipStream.close();
        assetStream.close();
        
        final int totalFiles = fileCount;
        runOnUiThread(() -> {
            progressDialog.setProgress(100);
            progressDialog.dismiss();
            android.widget.Toast.makeText(this, 
                "Cheats installed: " + totalFiles + " files (from ZIP)", 
                android.widget.Toast.LENGTH_LONG).show();
        });
        
        Log.i(TAG, "Cheats extracted from ZIP: " + totalFiles + " files");
    }
    
    /**
     * Interface de callback pour la copie de fichiers
     */
    private interface FileCopyCallback {
        void onFileProgress(String fileName);
    }
    
    /**
     * Interface de callback pour la progression par système
     */
    private interface SystemProgressCallback {
        void onSystemStart(String systemName, int systemIndex, int totalSystems);
        void onFileProgress(String fileName);
    }
    
    /**
     * Copie récursivement un dossier cheats avec progression par système
     */
    private int copyAssetFolderWithSystemCallback(String assetPath, String destPath, SystemProgressCallback callback) throws Exception {
        int filesCopied = 0;
        
        // Chemin vers retroarch (où sont les systèmes)
        String retroarchPath = assetPath + "/retroarch";
        String[] systems = getAssets().list(retroarchPath);
        
        if (systems == null || systems.length == 0) {
            Log.w(TAG, "No systems found in " + retroarchPath);
            return 0;
        }
        
        // Compter seulement les vrais systèmes (pas overrides)
        int totalSystems = 0;
        for (String system : systems) {
            if (!system.equals("overrides")) {
                totalSystems++;
            }
        }
        
        int systemIndex = 0;
        
        // Copier chaque système
        for (String system : systems) {
            if (system.equals("overrides")) {
                // Copier overrides à la fin sans notification
                filesCopied += copyAssetFolderWithCallback(retroarchPath + "/overrides", destPath + "/retroarch/overrides", callback::onFileProgress);
                continue;
            }
            
            systemIndex++;
            callback.onSystemStart(system, systemIndex, totalSystems);
            
            String systemAssetPath = retroarchPath + "/" + system;
            String systemDestPath = destPath + "/retroarch/" + system;
            
            filesCopied += copyAssetFolderWithCallback(systemAssetPath, systemDestPath, callback::onFileProgress);
        }
        
        // Copier aussi le dossier user si présent
        try {
            String userPath = assetPath + "/user";
            String[] userFiles = getAssets().list(userPath);
            if (userFiles != null && userFiles.length > 0) {
                filesCopied += copyAssetFolderWithCallback(userPath, destPath + "/user", callback::onFileProgress);
            }
        } catch (Exception e) {
            Log.d(TAG, "No user cheats folder");
        }
        
        return filesCopied;
    }
    
    /**
     * Copie récursivement un dossier depuis assets avec callback
     */
    private int copyAssetFolderWithCallback(String assetPath, String destPath, FileCopyCallback callback) throws Exception {
        int filesCopied = 0;
        
        String[] files = getAssets().list(assetPath);
        if (files == null || files.length == 0) {
            return 0;
        }
        
        // Créer le répertoire de destination
        File destDir = new File(destPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        for (String fileName : files) {
            String assetFilePath = assetPath + "/" + fileName;
            String destFilePath = destPath + "/" + fileName;
            
            try {
                // Vérifier si c'est un dossier ou un fichier
                String[] subFiles = getAssets().list(assetFilePath);
                if (subFiles != null && subFiles.length > 0) {
                    // C'est un dossier, copie récursive
                    filesCopied += copyAssetFolderWithCallback(assetFilePath, destFilePath, callback);
                } else {
                    // C'est un fichier
                    File destFile = new File(destFilePath);
                    if (!destFile.exists()) {
                        java.io.InputStream is = getAssets().open(assetFilePath);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        
                        is.close();
                        fos.close();
                        filesCopied++;
                        
                        // Notifier la progression
                        if (callback != null) {
                            callback.onFileProgress(fileName);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error copying " + assetFilePath + ": " + e.getMessage());
            }
        }
        
        return filesCopied;
    }
}

