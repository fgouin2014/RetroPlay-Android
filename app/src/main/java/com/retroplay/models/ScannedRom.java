package com.retroplay.models;

/**
 * Représente un ROM scanné lors de la génération de gamelist.
 * Extracted from ConsoleManagerActivity to improve modularity.
 */
public class ScannedRom {
    public String id;
    public String name;
    public String path;
    public boolean hasBox2dImage;
    public boolean hasScreenshot;
}
