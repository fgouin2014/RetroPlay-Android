package com.retroplay.ui.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Helper pour afficher le dialog de rapport d'audit
 * Extrait de ConsoleManagerActivity pour améliorer la modularité
 */
public class AuditReportDialog {
    
    /**
     * Affiche le dialog de rapport d'audit
     * @param context Le contexte de l'activité
     * @param report Le texte du rapport
     * @param success Nombre de consoles scannées avec succès
     * @param total Nombre total de consoles scannées
     */
    public static void show(Context context, String report, int success, int total) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("ROM Audit Report");
        
        // Créer un ScrollView pour le rapport
        ScrollView scrollView = new ScrollView(context);
        TextView textView = new TextView(context);
        textView.setText(report);
        textView.setTextSize(12);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Copy Report", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Audit Report", report);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }
}
