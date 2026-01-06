package com.retroplay.models;

import java.util.List;

/**
 * Configuration d'une console.
 * Extracted from ConsoleManagerActivity to improve modularity.
 */
public class ConsoleConfig {
    public String id;
    public String name;
    public String fullName;
    public String defaultCore;
    public String color;
    public boolean isGeneric;
    public boolean hasGamelist;  // Indique si gamelist.json existe
    public boolean usesAutoScan; // Indique si le scanner automatique est utilisé
    public List<String> cores;
    public List<String> extensions;
}
