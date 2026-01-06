package com.retroplay.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.retroplay.R;
import com.retroplay.models.ConsoleConfig;
import com.retroplay.models.ScannedRom;
import com.retroplay.usecases.GenerateGamelistUseCase;
import java.util.List;

/**
 * Adapter pour la RecyclerView des consoles dans ConsoleManagerActivity.
 * Extracted from ConsoleManagerActivity to improve modularity.
 */
public class ConsoleAdapter extends RecyclerView.Adapter<ConsoleAdapter.ConsoleViewHolder> {
    
    private List<ConsoleConfig> consoles;
    private final ConsoleActionsListener actionsListener;
    private final GenerateGamelistUseCase generateGamelistUseCase;
    
    /**
     * Interface pour les actions sur les consoles
     */
    public interface ConsoleActionsListener {
        void onEditConsole(ConsoleConfig console);
        void onRefreshConsole(ConsoleConfig console, String extensions, 
                              GenerateGamelistUseCase.ProgressCallback progressCallback,
                              GenerateGamelistUseCase.PreviewCallback previewCallback);
        void onArtworkConsole(ConsoleConfig console);
        void runOnUiThread(Runnable action);
    }
    
    public ConsoleAdapter(List<ConsoleConfig> consoles, ConsoleActionsListener actionsListener,
                          GenerateGamelistUseCase generateGamelistUseCase) {
        this.consoles = consoles;
        this.actionsListener = actionsListener;
        this.generateGamelistUseCase = generateGamelistUseCase;
        android.util.Log.i("ConsoleAdapter", "Adapter créé avec " + (consoles != null ? consoles.size() : 0) + " consoles");
        if (consoles != null) {
            for (ConsoleConfig c : consoles) {
                android.util.Log.d("ConsoleAdapter", "  - " + c.id + " -> " + c.name);
            }
        }
    }
    
    public void updateConsoles(List<ConsoleConfig> newConsoles) {
        this.consoles = newConsoles;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ConsoleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_console_manager, parent, false);
        return new ConsoleViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ConsoleViewHolder consoleHolder, int position) {
        ConsoleConfig console = consoles.get(position);
        
        // Log pour debug
        android.util.Log.d("ConsoleAdapter", "Binding position " + position + ": " + console.id + " -> " + console.name);
        
        consoleHolder.consoleName.setText(console.name);
        consoleHolder.consoleFullName.setText(console.fullName);
        
        consoleHolder.consoleCore.setText("Core: " + console.defaultCore);
        
        // Afficher le badge approprié
        if (console.usesAutoScan) {
            // Console sans gamelist.json - utilise le scanner automatique
            consoleHolder.autoScanBadge.setVisibility(View.VISIBLE);
            consoleHolder.genericBadge.setVisibility(View.GONE);
        } else if (console.isGeneric) {
            // Console générique
            consoleHolder.genericBadge.setVisibility(View.VISIBLE);
            consoleHolder.autoScanBadge.setVisibility(View.GONE);
        } else {
            // Console standard avec gamelist.json
            consoleHolder.genericBadge.setVisibility(View.GONE);
            consoleHolder.autoScanBadge.setVisibility(View.GONE);
        }
        
        consoleHolder.editButton.setOnClickListener(v -> actionsListener.onEditConsole(console));
        
        // Bouton refresh - scanner/regénérer le gamelist.json
        consoleHolder.refreshButton.setOnClickListener(v -> {
            String extensions = String.join(", ", console.extensions);
            
            GenerateGamelistUseCase.ProgressCallback progressCallback = new GenerateGamelistUseCase.ProgressCallback() {
                private android.app.ProgressDialog progressDialog;
                
                @Override
                public void showProgress(String title, String message, boolean indeterminate) {
                    actionsListener.runOnUiThread(() -> {
                        progressDialog = new android.app.ProgressDialog(consoleHolder.itemView.getContext());
                        progressDialog.setTitle(title);
                        progressDialog.setMessage(message);
                        progressDialog.setIndeterminate(indeterminate);
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    });
                }
                
                @Override
                public void dismissProgress() {
                    actionsListener.runOnUiThread(() -> {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    });
                }
                
                @Override
                public void showToast(String message, int duration) {
                    actionsListener.runOnUiThread(() -> {
                        android.widget.Toast.makeText(consoleHolder.itemView.getContext(), message, duration).show();
                    });
                }
            };
            
            GenerateGamelistUseCase.PreviewCallback previewCallback = (consoleId, roms, gamelistExists) -> {
                // Le callback sera géré par onRefreshConsole dans ConsoleManagerActivity
            };
            
            actionsListener.onRefreshConsole(console, extensions, progressCallback, previewCallback);
        });
        
        consoleHolder.artworkButton.setOnClickListener(v -> actionsListener.onArtworkConsole(console));
    }
    
    @Override
    public int getItemCount() {
        int count = consoles != null ? consoles.size() : 0;
        android.util.Log.d("ConsoleAdapter", "getItemCount() = " + count);
        return count;
    }
    
    static class ConsoleViewHolder extends RecyclerView.ViewHolder {
        TextView consoleIcon;
        TextView consoleName;
        TextView consoleFullName;
        TextView consoleCore;
        TextView genericBadge;
        TextView autoScanBadge;
        TextView editButton;
        TextView refreshButton;
        TextView artworkButton;
        
        ConsoleViewHolder(View itemView) {
            super(itemView);
            consoleIcon = itemView.findViewById(R.id.consoleIcon);
            consoleName = itemView.findViewById(R.id.consoleName);
            consoleFullName = itemView.findViewById(R.id.consoleFullName);
            consoleCore = itemView.findViewById(R.id.consoleCore);
            genericBadge = itemView.findViewById(R.id.consoleGenericBadge);
            autoScanBadge = itemView.findViewById(R.id.consoleAutoScanBadge);
            editButton = itemView.findViewById(R.id.editButton);
            refreshButton = itemView.findViewById(R.id.refreshButton);
            artworkButton = itemView.findViewById(R.id.artworkButton);
        }
    }
}














