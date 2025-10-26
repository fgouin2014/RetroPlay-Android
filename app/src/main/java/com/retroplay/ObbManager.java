package com.retroplay;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gestionnaire pour les fichiers OBB (Opaque Binary Blob)
 * Extrait les OBB vers le stockage interne de l'application
 */
public class ObbManager {
    private static final String TAG = "ObbManager";
    private static final String OBB_DIR = "obb";
    
    private Context context;
    
    public ObbManager(Context context) {
        this.context = context;
    }
    
    /**
     * Vérifie si les OBB sont déjà extraits
     */
    public boolean areObbExtracted() {
        File obbDir = new File(context.getFilesDir(), OBB_DIR);
        File nesgameDir = new File(obbDir, "nesgame_extracted");
        File mediaDir = new File(obbDir, "media_extracted");
        
        return nesgameDir.exists() && mediaDir.exists() && 
               new File(nesgameDir, "nes/gamelist.json").exists();
    }
    
    /**
     * Extrait les OBB depuis les assets vers le stockage interne
     */
    public boolean extractObbFiles() {
        try {
            Log.i(TAG, "Début de l'extraction des OBB");
            
            // Créer le répertoire OBB
            File obbDir = new File(context.getFilesDir(), OBB_DIR);
            if (!obbDir.exists()) {
                obbDir.mkdirs();
            }
            
            // Extraire le fichier de jeux
            boolean nesgameSuccess = extractObbFile("com.retroplay.nesgame.obb", "nesgame_extracted");
            boolean mediaSuccess = extractObbFile("com.retroplay.media.obb", "media_extracted");
            
            boolean success = nesgameSuccess && mediaSuccess;
            Log.i(TAG, "Extraction des OBB terminée: " + (success ? "SUCCÈS" : "ÉCHEC"));
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'extraction des OBB", e);
            return false;
        }
    }
    
    /**
     * Extrait un fichier OBB spécifique
     */
    private boolean extractObbFile(String obbFileName, String extractDirName) {
        try {
            Log.i(TAG, "Extraction de " + obbFileName);
            
            InputStream is = context.getAssets().open(obbFileName);
            ZipInputStream zis = new ZipInputStream(is);
            
            File extractDir = new File(context.getFilesDir(), OBB_DIR + "/" + extractDirName);
            if (!extractDir.exists()) {
                extractDir.mkdirs();
            }
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File entryFile = new File(extractDir, entryName);
                
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    // Créer les répertoires parents si nécessaire
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    // Écrire le fichier
                    FileOutputStream fos = new FileOutputStream(entryFile);
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.close();
                }
                
                zis.closeEntry();
            }
            
            zis.close();
            is.close();
            
            Log.i(TAG, "Extraction de " + obbFileName + " terminée");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'extraction de " + obbFileName, e);
            return false;
        }
    }
    
    /**
     * Obtient le chemin vers le gamelist.json
     */
    public String getGamelistPath() {
        return new File(context.getFilesDir(), OBB_DIR + "/nesgame_extracted/nes/gamelist.json").getAbsolutePath();
    }
    
    /**
     * Obtient le chemin vers un fichier de jeu
     */
    public String getGameFilePath(String gamePath) {
        // Enlever "./" du début du chemin
        String cleanPath = gamePath.startsWith("./") ? gamePath.substring(2) : gamePath;
        return new File(context.getFilesDir(), OBB_DIR + "/nesgame_extracted/nes/" + cleanPath).getAbsolutePath();
    }
    
    /**
     * Obtient le chemin vers une image box2d
     */
    public String getBox2dImagePath(String gameName) {
        return new File(context.getFilesDir(), OBB_DIR + "/media_extracted/nes/media/box2d/" + gameName + ".png").getAbsolutePath();
    }
    
    /**
     * Obtient le chemin vers une image screenshot
     */
    public String getScreenshotImagePath(String gameName) {
        return new File(context.getFilesDir(), OBB_DIR + "/media_extracted/nes/media/screenshot/" + gameName + ".png").getAbsolutePath();
    }
}

