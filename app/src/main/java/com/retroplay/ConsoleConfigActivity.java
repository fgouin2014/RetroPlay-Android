package com.retroplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsoleConfigActivity extends AppCompatActivity {
    
    private String currentConsole;
    private SharedPreferences prefs;
    
    // UI Components
    private TextView consoleTitleConfig;
    private Spinner presetSpinner;
    private SwitchCompat threadsSwitch;
    private SwitchCompat psxDpadSwitch;
    private android.view.View psxDpadContainer;
    private SwitchCompat customTabsSwitch;
    private android.view.View customTabsContainer;
    private SeekBar touchScaleSeekBar;
    private SeekBar touchAlphaSeekBar;
    private TextView touchScaleValue;
    private TextView touchAlphaValue;
    private MaterialButton saveButton;
    private MaterialButton editHtmlButton;
    
    // Available cores per console
    private static final String[] NES_CORES = {"fceumm", "nestopia"};
    private static final String[] SNES_CORES = {"snes9x"};
    private static final String[] N64_CORES = {"parallel_n64", "mupen64plus_next"};
    private static final String[] GENESIS_CORES = {"genesis_plus_gx", "picodrive"};
    private static final String[] GBA_CORES = {"mgba", "vba_next"};
    private static final String[] GB_CORES = {"gambatte", "sameboy"};
    private static final String[] GBC_CORES = {"gambatte", "sameboy"};
    private static final String[] NDS_CORES = {"desmume", "melonds"};
    private static final String[] PS1_CORES = {"pcsx_rearmed", "beetle_psx"};
    private static final String[] ARCADE_CORES = {"fbalpha2012", "mame2003_plus"};
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console_config);
        
        // Get console from intent
        currentConsole = getIntent().getStringExtra("console");
        if (currentConsole == null) {
            currentConsole = "nes";
        }
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences("console_config", Context.MODE_PRIVATE);
        
        // Setup views
        setupViews();
        loadConfiguration();
    }
    
    private void setupViews() {
        // Header
        TextView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
        
        consoleTitleConfig = findViewById(R.id.consoleTitleConfig);
        consoleTitleConfig.setText(currentConsole.toUpperCase() + " - ADVANCED CONFIG");
        
        // Preset spinner
        presetSpinner = findViewById(R.id.presetSpinner);
        setupPresetSpinner();
        
        // Performance settings
        threadsSwitch = findViewById(R.id.threadsSwitch);
        
        // PSX-specific settings
        psxDpadSwitch = findViewById(R.id.psxDpadSwitch);
        psxDpadContainer = findViewById(R.id.psxDpadContainer);
        
        // Show D-Pad option for PSX and PSP (both support analog/digital controls)
        if (currentConsole.equals("psx") || currentConsole.equals("ps1") || currentConsole.equals("playstation") || currentConsole.equals("psp")) {
            psxDpadContainer.setVisibility(android.view.View.VISIBLE);
        } else {
            psxDpadContainer.setVisibility(android.view.View.GONE);
        }
        
        // Custom Tabs option (for SharedArrayBuffer support)
        customTabsSwitch = findViewById(R.id.customTabsSwitch);
        customTabsContainer = findViewById(R.id.customTabsContainer);
        
        // Control settings
        touchScaleSeekBar = findViewById(R.id.touchScaleSeekBar);
        touchAlphaSeekBar = findViewById(R.id.touchAlphaSeekBar);
        touchScaleValue = findViewById(R.id.touchScaleValue);
        touchAlphaValue = findViewById(R.id.touchAlphaValue);
        
        // SeekBar listeners
        touchScaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = 0.5f + (progress / 20.0f) * 1.5f; // 0.5 to 2.0
                touchScaleValue.setText(String.format("%.1fx", scale));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        touchAlphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = progress / 10.0f; // 0.0 to 1.0
                touchAlphaValue.setText(String.format("%.1f", alpha));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Save button
        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveConfiguration());
        
        // Edit HTML button
        editHtmlButton = findViewById(R.id.editHtmlButton);
        editHtmlButton.setOnClickListener(v -> openHtmlEditor());
    }
    
    private void setupPresetSpinner() {
        // Get console-specific presets
        String[] presets = getPresetsForConsole(currentConsole);
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            R.layout.spinner_item,
            presets
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        
        // Add listener to apply preset when selected
        presetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedPreset = parent.getItemAtPosition(position).toString();
                if (!selectedPreset.equals("Custom (edit HTML)")) {
                    applyPresetValues(selectedPreset);
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Do nothing
            }
        });
    }
    
    private String getDefaultPresetForConsole(String console) {
        switch (console.toLowerCase()) {
            case "nes": return "NES Native (Default)";
            case "snes": return "SNES Native (Default)";
            case "n64": return "N64 Native (Default)";
            case "megadrive":
            case "genesis": return "Mega Drive 6-Button (Default)";
            case "psx":
            case "ps1":
            case "playstation": return "PSX DualShock (Analog)";
            case "gb": return "Game Boy Native (Default)";
            case "gbc": return "GBC Native (Default)";
            case "gba": return "GBA Native (Default)";
            case "psp": return "PSP Analog (Default)";
            default: return "Default (Auto-detect)";
        }
    }
    
    private String[] getPresetsForConsole(String console) {
        switch (console.toLowerCase()) {
            case "nes":
                return new String[]{
                    "NES Native (Default)",
                    "NES Compact",
                    "NES Large Buttons",
                    "Custom (edit HTML)"
                };
            case "snes":
                return new String[]{
                    "SNES Native (Default)",
                    "SNES Compact",
                    "SNES 4-Button Only",
                    "Custom (edit HTML)"
                };
            case "n64":
                return new String[]{
                    "N64 Native (Default)",
                    "N64 C-Buttons Right",
                    "N64 C-Buttons Bottom",
                    "Custom (edit HTML)"
                };
            case "megadrive":
            case "genesis":
                return new String[]{
                    "Mega Drive 6-Button (Default)",
                    "Genesis 3-Button",
                    "Mega Drive Compact",
                    "Custom (edit HTML)"
                };
            case "psx":
            case "ps1":
            case "playstation":
                return new String[]{
                    "PSX DualShock (Analog)",
                    "PSX Digital (D-Pad Only)",
                    "PSX Compact Layout",
                    "Custom (edit HTML)"
                };
            case "gb":
                return new String[]{
                    "Game Boy Native (Default)",
                    "Game Boy Compact",
                    "Game Boy Large",
                    "Custom (edit HTML)"
                };
            case "gbc":
                return new String[]{
                    "GBC Native (Default)",
                    "GBC Compact",
                    "GBC Large",
                    "Custom (edit HTML)"
                };
            case "gba":
                return new String[]{
                    "GBA Native (Default)",
                    "GBA Compact",
                    "GBA Large Shoulders",
                    "Custom (edit HTML)"
                };
            case "psp":
                return new String[]{
                    "PSP Analog (Default)",
                    "PSP Digital (D-Pad Only)",
                    "PSP Compact",
                    "PSP Large Analog",
                    "Custom (edit HTML)"
                };
            default:
                return new String[]{
                    "Default (Auto-detect)",
                    "Compact",
                    "Large Buttons",
                    "Custom (edit HTML)"
                };
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadConfiguration() {
        String prefix = currentConsole + "_";
        
        // Load preset selection (get default preset for this console)
        String defaultPreset = getDefaultPresetForConsole(currentConsole);
        String preset = prefs.getString(prefix + "preset", defaultPreset);
        ArrayAdapter<String> presetAdapter = (ArrayAdapter<String>) presetSpinner.getAdapter();
        int presetPosition = presetAdapter.getPosition(preset);
        if (presetPosition >= 0) {
            presetSpinner.setSelection(presetPosition);
        }
        
        // Load performance settings (default threads for heavy consoles)
        boolean defaultThreads = currentConsole.equals("psp") || currentConsole.equals("n64");
        threadsSwitch.setChecked(prefs.getBoolean(prefix + "threads", defaultThreads));
        
        // Load PSX D-Pad setting
        if (psxDpadSwitch != null) {
            psxDpadSwitch.setChecked(prefs.getBoolean(prefix + "use_dpad", false));
        }
        
        // Load Custom Tabs setting (default: true for PSP, false for others)
        boolean defaultCustomTabs = currentConsole.equals("psp");
        if (customTabsSwitch != null) {
            customTabsSwitch.setChecked(prefs.getBoolean(prefix + "use_custom_tabs", defaultCustomTabs));
        }
        
        // Load control settings
        float touchScale = prefs.getFloat(prefix + "touch_scale", 1.0f);
        int scaleProgress = (int) ((touchScale - 0.5f) / 1.5f * 20);
        touchScaleSeekBar.setProgress(scaleProgress);
        touchScaleValue.setText(String.format("%.1fx", touchScale));
        
        float touchAlpha = prefs.getFloat(prefix + "touch_alpha", 0.8f);
        int alphaProgress = (int) (touchAlpha * 10);
        touchAlphaSeekBar.setProgress(alphaProgress);
        touchAlphaValue.setText(String.format("%.1f", touchAlpha));
    }
    
    private void saveConfiguration() {
        String prefix = currentConsole + "_";
        SharedPreferences.Editor editor = prefs.edit();
        
        // Save preset selection
        String selectedPreset = presetSpinner.getSelectedItem().toString();
        editor.putString(prefix + "preset", selectedPreset);
        
        // If "Custom" is selected, open HTML editor
        if (selectedPreset.equals("Custom (edit HTML)")) {
            editor.apply();
            openHtmlEditor();
            return;
        }
        
        // Note: Preset values are already applied when user selects preset from spinner
        // We just save the current UI values (which may have been modified manually)
        
        // Save performance settings
        boolean threads = threadsSwitch.isChecked();
        editor.putBoolean(prefix + "threads", threads);
        
        // Save PSX D-Pad setting
        if (psxDpadSwitch != null) {
            boolean useDpad = psxDpadSwitch.isChecked();
            editor.putBoolean(prefix + "use_dpad", useDpad);
        }
        
        // Save Custom Tabs setting
        if (customTabsSwitch != null) {
            boolean useCustomTabs = customTabsSwitch.isChecked();
            editor.putBoolean(prefix + "use_custom_tabs", useCustomTabs);
        }
        
        // Save control settings
        float touchScale = 0.5f + (touchScaleSeekBar.getProgress() / 20.0f) * 1.5f;
        editor.putFloat(prefix + "touch_scale", touchScale);
        
        float touchAlpha = touchAlphaSeekBar.getProgress() / 10.0f;
        editor.putFloat(prefix + "touch_alpha", touchAlpha);
        
        editor.apply();
        
        // Log saved values
        System.out.println("===== Advanced Configuration Saved =====");
        System.out.println("Console: " + currentConsole);
        System.out.println("Preset: " + selectedPreset);
        System.out.println("Threads: " + threads);
        System.out.println("Touch Scale: " + touchScale);
        System.out.println("Touch Alpha: " + touchAlpha);
        System.out.println("(Core/Video managed by EmulatorJS settings)");
        System.out.println("=======================================");
        
        // Show feedback and close
        android.widget.Toast.makeText(this, "Advanced configuration saved", android.widget.Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private String getDefaultCore() {
        switch (currentConsole) {
            case "snes":
                return "snes9x";
            case "n64":
                return "parallel_n64";
            case "nes":
            default:
                return "fceumm";
        }
    }
    
    /**
     * Get console configuration
     */
    public static ConsoleConfig getConfig(Context context, String console) {
        SharedPreferences prefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE);
        String prefix = console + "_";
        
        ConsoleConfig config = new ConsoleConfig();
        config.console = console;
        
        // Core n'est plus géré ici - EmulatorJS le gère via localStorage
        config.core = null;
        
        // Charger les paramètres avancés (non accessibles dans GUI EmulatorJS)
        config.threads = prefs.getBoolean(prefix + "threads", false);
        config.touchScale = prefs.getFloat(prefix + "touch_scale", 1.0f);
        config.touchAlpha = prefs.getFloat(prefix + "touch_alpha", 0.8f);
        config.useDpad = prefs.getBoolean(prefix + "use_dpad", false);
        
        // Custom Tabs (default: true for PSP, false for others)
        boolean defaultCustomTabs = console.equals("psp");
        config.useCustomTabs = prefs.getBoolean(prefix + "use_custom_tabs", defaultCustomTabs);
        
        System.out.println("===== Loading Advanced Configuration =====");
        System.out.println("Console: " + console);
        System.out.println("Threads: " + config.threads);
        System.out.println("Touch Scale: " + config.touchScale);
        System.out.println("Touch Alpha: " + config.touchAlpha);
        System.out.println("Use D-Pad (PSX): " + config.useDpad);
        System.out.println("Use Custom Tabs: " + config.useCustomTabs);
        System.out.println("(Core/Video managed by EmulatorJS settings)");
        System.out.println("==========================================");
        
        return config;
    }
    
    private static String readCoreFromConsoleJson(String console) {
        try {
            java.io.File consoleJsonFile = new java.io.File(
                "/storage/emulated/0/GameLibrary-Data/" + console + "/console.json");
            
            if (consoleJsonFile.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(consoleJsonFile);
                byte[] buffer = new byte[(int) consoleJsonFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer, "UTF-8");
                
                org.json.JSONObject config = new org.json.JSONObject(json);
                String defaultCore = config.optString("defaultCore", null);
                
                if (defaultCore != null && !defaultCore.isEmpty()) {
                    android.util.Log.d("ConsoleConfig", "Loaded core from console.json for " + console + ": " + defaultCore);
                    return defaultCore;
                }
            }
        } catch (Exception e) {
            android.util.Log.w("ConsoleConfig", "Could not read console.json for " + console, e);
        }
        return null;
    }
    
    private static String getDefaultCoreStatic(String console) {
        switch (console.toLowerCase()) {
            // Nintendo consoles
            case "nes":
            case "famicom":
                return "fceumm";
            case "snes":
            case "sfc":
                return "snes9x";
            case "n64":
                return "parallel_n64";
            case "gb":
                return "gambatte";
            case "gbc":
                return "gambatte";
            case "gba":
                return "mgba";
            case "nds":
            case "ds":
                return "melonds";
            
            // Sega consoles
            case "genesis":
            case "megadrive":
            case "md":
                return "genesis_plus_gx";
            case "mastersystem":
            case "sms":
                return "genesis_plus_gx";
            case "gamegear":
            case "gg":
                return "genesis_plus_gx";
            case "sega32x":
            case "32x":
                return "picodrive";
            case "segacd":
            case "megacd":
                return "genesis_plus_gx";
            case "saturn":
                return "yabause";
            case "dreamcast":
            case "dc":
                return "flycast";
            
            // Sony consoles
            case "ps1":
            case "psx":
            case "playstation":
                return "pcsx_rearmed";
            case "psp":
                return "ppsspp";
            
            // Atari
            case "atari2600":
            case "2600":
                return "stella2014";
            case "atari5200":
            case "5200":
                return "a5200";
            case "atari7800":
            case "7800":
                return "prosystem";
            case "lynx":
                return "handy";
            case "jaguar":
                return "virtualjaguar";
            
            // Other systems
            case "3do":
                return "opera";
            
            // Arcade
            case "arcade":
                return "fbneo";
            case "mame":
                return "mame2010";
            case "fbneo":
                return "fbneo";
            case "cps1":
                return "fbalpha2012_cps1";
            case "cps2":
                return "fbalpha2012_cps2";
            case "cps3":
                return "fbneo";
            
            case "neogeo":
                return "fbneo";
            case "ngp":
                return "mednafen_ngp";
            case "wonderswan":
            case "ws":
            case "wsc":
                return "mednafen_wswan";
            case "pcengine":
            case "turbografx":
            case "pce":
                return "mednafen_pce";
            case "virtualboy":
            case "vb":
                return "beetle_vb";
            case "colecovision":
            case "coleco":
                return "gearcoleco";
            case "dos":
                return "dosbox_pure";
            case "amiga":
                return "puae";
            case "c64":
            case "commodore64":
                return "vice_x64";
            
            // Default
            default:
                return "auto";
        }
    }
    
    /**
     * Ouvre emulator.html avec un éditeur de texte externe
     */
    private void applyPresetValues(String presetName) {
        // Apply preset values to UI controls based on preset name
        switch (presetName) {
            // NES Presets
            case "NES Native (Default)":
                touchScaleSeekBar.setProgress(10); // 1.0x
                touchAlphaSeekBar.setProgress(8);  // 0.8
                break;
            case "NES Compact":
                touchScaleSeekBar.setProgress(7);  // 0.8x
                touchAlphaSeekBar.setProgress(9);  // 0.9
                break;
            case "NES Large Buttons":
                touchScaleSeekBar.setProgress(15); // 1.5x
                touchAlphaSeekBar.setProgress(7);  // 0.7
                break;
                
            // SNES Presets
            case "SNES Native (Default)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                break;
            case "SNES Compact":
                touchScaleSeekBar.setProgress(7);
                touchAlphaSeekBar.setProgress(9);
                break;
            case "SNES 4-Button Only":
                touchScaleSeekBar.setProgress(12);
                touchAlphaSeekBar.setProgress(8);
                break;
                
            // N64 Presets
            case "N64 Native (Default)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                break;
            case "N64 C-Buttons Right":
            case "N64 C-Buttons Bottom":
                touchScaleSeekBar.setProgress(11);
                touchAlphaSeekBar.setProgress(8);
                break;
                
            // Mega Drive Presets
            case "Mega Drive 6-Button (Default)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                break;
            case "Genesis 3-Button":
                touchScaleSeekBar.setProgress(12);
                touchAlphaSeekBar.setProgress(8);
                break;
            case "Mega Drive Compact":
                touchScaleSeekBar.setProgress(7);
                touchAlphaSeekBar.setProgress(9);
                break;
                
            // PSX Presets
            case "PSX DualShock (Analog)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                if (psxDpadSwitch != null) psxDpadSwitch.setChecked(false); // Analog sticks
                break;
            case "PSX Digital (D-Pad Only)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                if (psxDpadSwitch != null) psxDpadSwitch.setChecked(true);  // D-Pad only
                break;
            case "PSX Compact Layout":
                touchScaleSeekBar.setProgress(7);
                touchAlphaSeekBar.setProgress(9);
                break;
                
            // Game Boy Presets
            case "Game Boy Native (Default)":
            case "GBC Native (Default)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                break;
            case "Game Boy Compact":
            case "GBC Compact":
                touchScaleSeekBar.setProgress(6);
                touchAlphaSeekBar.setProgress(9);
                break;
            case "Game Boy Large":
            case "GBC Large":
                touchScaleSeekBar.setProgress(14);
                touchAlphaSeekBar.setProgress(7);
                break;
                
            // GBA Presets
            case "GBA Native (Default)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                break;
            case "GBA Compact":
                touchScaleSeekBar.setProgress(7);
                touchAlphaSeekBar.setProgress(9);
                break;
            case "GBA Large Shoulders":
                touchScaleSeekBar.setProgress(13);
                touchAlphaSeekBar.setProgress(7);
                break;
                
            // PSP Presets
            case "PSP Analog (Default)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                if (psxDpadSwitch != null) psxDpadSwitch.setChecked(false); // Analog sticks
                break;
            case "PSP Digital (D-Pad Only)":
                touchScaleSeekBar.setProgress(10);
                touchAlphaSeekBar.setProgress(8);
                if (psxDpadSwitch != null) psxDpadSwitch.setChecked(true);  // D-Pad only
                break;
            case "PSP Compact":
                touchScaleSeekBar.setProgress(7);
                touchAlphaSeekBar.setProgress(9);
                if (psxDpadSwitch != null) psxDpadSwitch.setChecked(false); // Analog
                break;
            case "PSP Large Analog":
                touchScaleSeekBar.setProgress(13);
                touchAlphaSeekBar.setProgress(7);
                if (psxDpadSwitch != null) psxDpadSwitch.setChecked(false); // Analog
                break;
                
            // Default fallback
            default:
                touchScaleSeekBar.setProgress(10); // 1.0x
                touchAlphaSeekBar.setProgress(8);  // 0.8
                break;
        }
        
        // Update value displays
        float scale = 0.5f + (touchScaleSeekBar.getProgress() / 20.0f) * 1.5f;
        touchScaleValue.setText(String.format("%.1fx", scale));
        float alpha = touchAlphaSeekBar.getProgress() / 10.0f;
        touchAlphaValue.setText(String.format("%.1f", alpha));
        
        android.util.Log.d("ConsoleConfig", "Applied preset: " + presetName + 
                          " (scale=" + scale + ", alpha=" + alpha + ")");
    }
    
    @Deprecated
    private void applyPresetToHtml(String presetName) {
        String htmlPath = "/storage/emulated/0/RetroPlay-Files/sites/emulator.html";
        java.io.File htmlFile = new java.io.File(htmlPath);
        
        if (!htmlFile.exists()) {
            android.util.Log.w("ConsoleConfig", "emulator.html not found in storage");
            return;
        }
        
        try {
            // Read the entire HTML file
            java.io.FileInputStream fis = new java.io.FileInputStream(htmlFile);
            byte[] buffer = new byte[(int) htmlFile.length()];
            fis.read(buffer);
            fis.close();
            String htmlContent = new String(buffer, "UTF-8");
            
            // Find the EJS_VirtualGamepadSettings section
            String settingsStart = "window.EJS_VirtualGamepadSettings = ";
            int startIndex = htmlContent.indexOf(settingsStart);
            
            if (startIndex == -1) {
                android.util.Log.w("ConsoleConfig", "EJS_VirtualGamepadSettings not found in HTML");
                return;
            }
            
            // Determine what to inject based on preset
            String presetContent = getPresetContent(presetName);
            
            // Replace the existing EJS_VirtualGamepadSettings with the preset
            int settingsEnd;
            if (htmlContent.charAt(startIndex + settingsStart.length()) == '{') {
                // It's an object, find the closing }
                settingsEnd = htmlContent.indexOf("};", startIndex) + 2;
            } else if (htmlContent.charAt(startIndex + settingsStart.length()) == '[') {
                // It's an array, find the closing ]
                settingsEnd = findClosingBracket(htmlContent, startIndex + settingsStart.length()) + 2;
            } else {
                android.util.Log.w("ConsoleConfig", "Unexpected EJS_VirtualGamepadSettings format");
                return;
            }
            
            String newHtmlContent = htmlContent.substring(0, startIndex) +
                                   settingsStart + presetContent + ";" +
                                   htmlContent.substring(settingsEnd);
            
            // Write back to file
            java.io.FileOutputStream fos = new java.io.FileOutputStream(htmlFile);
            fos.write(newHtmlContent.getBytes("UTF-8"));
            fos.close();
            
            android.util.Log.d("ConsoleConfig", "Applied preset: " + presetName + " to emulator.html");
            
        } catch (Exception e) {
            android.util.Log.e("ConsoleConfig", "Error applying preset to HTML: " + e.getMessage());
            android.widget.Toast.makeText(this, "Error applying preset: " + e.getMessage(), 
                                        android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    private int findClosingBracket(String content, int startIndex) {
        int depth = 0;
        for (int i = startIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    
    private String getPresetContent(String presetName) {
        if (presetName.equals("Genesis 6-Button")) {
            return getGenesisPreset();
        } else if (presetName.equals("SNES")) {
            return getSnesPreset();
        } else {
            // Default (Auto-detect)
            return "{\n" +
                   "                enabled: true,\n" +
                   "                opacity: 0.8,\n" +
                   "                scale: 1.0\n" +
                   "            }";
        }
    }
    
    private String getGenesisPreset() {
        return "[\n" +
               "                // D-Pad gauche\n" +
               "                { type: \"dpad\", location: \"left\", left: \"10%\", top: \"50%\", joystickInput: false, inputValues: [4, 5, 6, 7] },\n" +
               "                // Bouton A\n" +
               "                { type: \"button\", text: \"A\", id: \"btn_a\", location: \"right\", right: 80, top: 120, bold: true, fontSize: 24, input_value: 8 },\n" +
               "                // Bouton B\n" +
               "                { type: \"button\", text: \"B\", id: \"btn_b\", location: \"right\", right: 20, top: 80, bold: true, fontSize: 24, input_value: 0 },\n" +
               "                // Bouton C\n" +
               "                { type: \"button\", text: \"C\", id: \"btn_c\", location: \"right\", right: 140, top: 80, bold: true, fontSize: 24, input_value: 9 },\n" +
               "                // Bouton X\n" +
               "                { type: \"button\", text: \"X\", id: \"btn_x\", location: \"right\", right: 80, top: 40, bold: true, fontSize: 20, input_value: 1 },\n" +
               "                // Bouton Y\n" +
               "                { type: \"button\", text: \"Y\", id: \"btn_y\", location: \"right\", right: 20, top: 10, bold: true, fontSize: 20, input_value: 10 },\n" +
               "                // Bouton Z\n" +
               "                { type: \"button\", text: \"Z\", id: \"btn_z\", location: \"right\", right: 140, top: 10, bold: true, fontSize: 20, input_value: 11 },\n" +
               "                // Start\n" +
               "                { type: \"button\", text: \"START\", id: \"start\", location: \"center\", left: 60, fontSize: 15, block: true, input_value: 3 },\n" +
               "                // Select/Mode\n" +
               "                { type: \"button\", text: \"SELECT\", id: \"select\", location: \"center\", left: -5, fontSize: 15, block: true, input_value: 2 }\n" +
               "            ]";
    }
    
    private String getSnesPreset() {
        return "[\n" +
               "                // D-Pad\n" +
               "                { type: \"dpad\", location: \"left\", left: \"10%\", top: \"50%\", joystickInput: false, inputValues: [4, 5, 6, 7] },\n" +
               "                // Boutons face (Y, X, B, A)\n" +
               "                { type: \"button\", text: \"Y\", id: \"y\", location: \"right\", right: 40, top: 40, bold: true, fontSize: 24, input_value: 9 },\n" +
               "                { type: \"button\", text: \"X\", id: \"x\", location: \"right\", right: 100, top: 80, bold: true, fontSize: 24, input_value: 1 },\n" +
               "                { type: \"button\", text: \"B\", id: \"b\", location: \"right\", right: 100, top: 120, bold: true, fontSize: 24, input_value: 8 },\n" +
               "                { type: \"button\", text: \"A\", id: \"a\", location: \"right\", right: 160, top: 80, bold: true, fontSize: 24, input_value: 0 },\n" +
               "                // L/R triggers\n" +
               "                { type: \"button\", text: \"L\", id: \"l\", location: \"left\", left: 20, top: 10, fontSize: 18, input_value: 10 },\n" +
               "                { type: \"button\", text: \"R\", id: \"r\", location: \"right\", right: 20, top: 10, fontSize: 18, input_value: 11 },\n" +
               "                // Start/Select\n" +
               "                { type: \"button\", text: \"START\", id: \"start\", location: \"center\", left: 60, fontSize: 14, block: true, input_value: 3 },\n" +
               "                { type: \"button\", text: \"SELECT\", id: \"select\", location: \"center\", left: -5, fontSize: 14, block: true, input_value: 2 }\n" +
               "            ]";
    }
    
    private void openHtmlEditor() {
        String htmlPath = "/storage/emulated/0/RetroPlay-Files/sites/emulator.html";
        java.io.File htmlFile = new java.io.File(htmlPath);
        
        if (!htmlFile.exists()) {
            android.widget.Toast.makeText(this, "emulator.html not found in storage", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        
        // Afficher dialog avec options
        new android.app.AlertDialog.Builder(this)
            .setTitle("Edit emulator.html")
            .setMessage("Location:\n" + htmlPath + "\n\n" +
                       "Click OPEN FOLDER to navigate to the file in your file manager, " +
                       "then open emulator.html with a text editor.\n\n" +
                       "Recommended apps:\n• QuickEdit\n• Acode\n• Total Commander")
            .setPositiveButton("OPEN FOLDER", (dialog, which) -> {
                openFileManagerToFolder();
            })
            .setNeutralButton("COPY PATH", (dialog, which) -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("HTML Path", htmlPath);
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(this, "Path copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }
    
    /**
     * Ouvre l'explorateur de fichiers sur le répertoire gamelibrary
     */
    private void openFileManagerToFolder() {
        try {
            // Essayer d'ouvrir directement le dossier avec l'explorateur de fichiers
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            android.net.Uri folderUri = android.net.Uri.parse("file:///storage/emulated/0/GameLibrary-Data/");
            intent.setDataAndType(folderUri, "resource/folder");
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback: essayer avec ACTION_GET_CONTENT
                intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra("android.provider.extra.INITIAL_URI", folderUri);
                startActivity(android.content.Intent.createChooser(intent, "Select File Manager"));
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(this, 
                "Could not open folder. Use any file manager to navigate to:\n" + 
                "/storage/emulated/0/GameLibrary-Data/", 
                android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Console configuration data class
     */
    public static class ConsoleConfig {
        public String console;
        public String core; // Null - géré par EmulatorJS
        public boolean threads; // EJS_threads
        public float touchScale; // EJS_VirtualGamepadSettings.scale
        public float touchAlpha; // EJS_VirtualGamepadSettings.opacity
        public boolean useDpad; // PSX only: Use D-Pad instead of analog sticks
        public boolean useCustomTabs; // Use Chrome Custom Tabs instead of WebView (for SharedArrayBuffer support)
    }
}

