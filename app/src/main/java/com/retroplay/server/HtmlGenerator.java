package com.retroplay.server;

import com.retroplay.utils.FileIconHelper;
import com.retroplay.utils.WebServerUtils;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Helper class for HTML generation.
 * Extracted from WebServer.java to improve modularity and testability.
 */
public class HtmlGenerator {
    
    /**
     * Génère un listing HTML simple d'un répertoire
     * 
     * @param directory Le répertoire à lister
     * @param path Le chemin URL du répertoire
     * @return Le HTML généré
     */
    public static String generateSimpleDirectoryListing(File directory, String path) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>Directory Listing</title></head><body>\n");
        html.append("<h1>Directory: ").append(escapeHtml(path)).append("</h1>\n");
        html.append("<ul>\n");
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    html.append("<li><a href=\"").append(escapeHtml(file.getName())).append("\">")
                        .append(escapeHtml(file.getName())).append("</a></li>\n");
                }
            }
        }
        
        html.append("</ul>\n</body></html>");
        return html.toString();
    }
    
    /**
     * Génère un listing HTML stylisé pour Game Library
     * 
     * @param directoryPath Le chemin du répertoire
     * @param files La liste des noms de fichiers
     * @return Le HTML généré
     */
    public static String generateGameLibraryDirectoryListing(String directoryPath, String[] files) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><title>Index of /gamelibrary/").append(escapeHtml(directoryPath)).append("</title>\n");
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
        html.append("<h1>📁 Game Library - ").append(escapeHtml(directoryPath)).append("</h1>\n");
        html.append("<a href=\"/gamelibrary/\" class=\"back-link\">← Back to Game Library</a>\n");
        html.append("<table>\n");
        html.append("<thead><tr><th>Name</th><th>Type</th><th>Size</th></tr></thead>\n");
        html.append("<tbody>\n");
        
        // Ajouter les fichiers
        if (files != null) {
            for (String file : files) {
                String fileUrl = "/gamelibrary/" + directoryPath + file;
                String fileIcon = FileIconHelper.getFileIcon(file);
                String fileType = FileIconHelper.getFileType(file);
                
                html.append("<tr>\n");
                html.append("<td><span class=\"file-icon\">").append(fileIcon).append("</span>");
                html.append("<a href=\"").append(escapeHtml(fileUrl)).append("\">").append(escapeHtml(file)).append("</a></td>\n");
                html.append("<td>").append(escapeHtml(fileType)).append("</td>\n");
                html.append("<td>-</td>\n"); // Taille non disponible pour les assets
                html.append("</tr>\n");
            }
        }
        
        html.append("</tbody></table>\n");
        html.append("</body></html>\n");
        return html.toString();
    }
    
    /**
     * Génère un listing HTML avec options Apache-like
     * 
     * @param urlPath Le chemin URL
     * @param files La liste des fichiers (chemins)
     * @param foldersFirst Si true, les dossiers sont listés en premier
     * @param exactSize Si true, affiche la taille exacte en bytes
     * @param customCSS CSS personnalisé (peut être vide)
     * @return Le HTML généré
     */
    public static String generateDirectoryListing(
            String urlPath,
            List<FileInfo> files,
            boolean foldersFirst,
            boolean exactSize,
            String customCSS) {
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><title>Index of ").append(escapeHtml(urlPath)).append("</title>\n");
        
        // CSS personnalisé (comme Apache)
        if (customCSS != null && !customCSS.isEmpty()) {
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
        html.append("<h1>Index of ").append(escapeHtml(urlPath)).append("</h1>\n");
        html.append("<hr>\n");
        html.append("<table>\n");
        html.append("<tr><th>Name</th><th>Last modified</th><th>Size</th></tr>\n");
        
        // Trier les fichiers si nécessaire
        if (foldersFirst) {
            files.sort((a, b) -> {
                if (a.isDirectory && !b.isDirectory) return -1;
                if (!a.isDirectory && b.isDirectory) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });
        } else {
            files.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        
        // Générer les lignes du tableau
        for (FileInfo fileInfo : files) {
            html.append("<tr><td>");
            if (fileInfo.isDirectory) {
                html.append("<a href=\"").append(escapeHtml(fileInfo.name)).append("/\">")
                    .append(escapeHtml(fileInfo.name)).append("/</a>");
            } else {
                html.append("<a href=\"").append(escapeHtml(fileInfo.name)).append("\">")
                    .append(escapeHtml(fileInfo.name)).append("</a>");
            }
            html.append("</td><td>").append(escapeHtml(fileInfo.lastModified)).append("</td><td>");
            if (fileInfo.size == -1) {
                html.append("-");
            } else {
                if (exactSize) {
                    html.append(fileInfo.size).append(" bytes");
                } else {
                    html.append(WebServerUtils.formatFileSize(fileInfo.size));
                }
            }
            html.append("</td></tr>\n");
        }
        
        html.append("</table>\n");
        html.append("<hr>\n");
        html.append("<address>ChatAI WebServer/1.0 (Android) Server at localhost Port 7777</address>\n");
        html.append("</body></html>\n");
        
        return html.toString();
    }
    
    /**
     * Échappe les caractères HTML spéciaux
     * 
     * @param text Le texte à échapper
     * @return Le texte échappé
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Classe interne pour représenter les informations d'un fichier
     */
    public static class FileInfo {
        public final String name;
        public final boolean isDirectory;
        public final String lastModified;
        public final long size;
        
        public FileInfo(String name, boolean isDirectory, String lastModified, long size) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.lastModified = lastModified;
            this.size = size;
        }
        
        public static FileInfo fromFile(File file) {
            String name = file.getName();
            boolean isDirectory = file.isDirectory();
            String lastModified = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(file.lastModified()));
            long size = isDirectory ? -1 : file.length();
            return new FileInfo(name, isDirectory, lastModified, size);
        }
    }
}

