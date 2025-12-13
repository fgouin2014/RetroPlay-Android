package com.retroplay.usecases;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Use Case pour charger la liste des cores disponibles
 * Extrait de ConsoleManagerActivity pour améliorer la modularité
 */
public class LoadCoresUseCase {
    private static final String TAG = "LoadCoresUseCase";
    private static final String GAMELIBRARY_DIR = "/storage/emulated/0/GameLibrary-Data";
    
    /**
     * Charge les cores disponibles depuis cores.json
     * @param callback Callback appelé avec la liste des cores chargés
     */
    public void loadAvailableCores(LoadCoresCallback callback) {
        new Thread(() -> {
            try {
                File coresFile = new File(GAMELIBRARY_DIR + "/data/cores/cores.json");
                if (coresFile.exists()) {
                    FileInputStream fis = new FileInputStream(coresFile);
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
                    
                    callback.onCoresLoaded(tempCores);
                    Log.i(TAG, "Loaded " + tempCores.size() + " available cores from cores.json");
                } else {
                    Log.w(TAG, "cores.json not found, using fallback cores");
                    callback.onCoresLoaded(getFallbackCores());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cores.json", e);
                callback.onCoresLoaded(getFallbackCores());
            }
        }).start();
    }
    
    /**
     * Retourne la liste des cores de fallback
     */
    public List<String> getFallbackCores() {
        List<String> cores = new ArrayList<>();
        cores.add("auto");
        // Arcade cores
        cores.add("fbneo");
        cores.add("mame2010");
        cores.add("mame2003_plus");
        cores.add("mame2003");
        cores.add("fbalpha2012_cps1");
        cores.add("fbalpha2012_cps2");
        cores.add("flycast");
        // Console cores
        cores.add("fceumm");
        cores.add("nestopia");
        cores.add("snes9x");
        cores.add("parallel_n64");
        cores.add("mupen64plus_next");
        cores.add("mupen64plus_next_gles3");
        cores.add("mupen64plus_next_gles2");
        cores.add("mgba");
        cores.add("gambatte");
        cores.add("melonds");
        cores.add("desmume");
        cores.add("pcsx_rearmed");
        cores.add("genesis_plus_gx");
        cores.add("picodrive");
        return cores;
    }
    
    /**
     * Interface pour le callback de chargement des cores
     */
    public interface LoadCoresCallback {
        void onCoresLoaded(List<String> cores);
    }
}
