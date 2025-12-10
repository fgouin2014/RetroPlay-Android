package com.retroplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
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
    private SwitchCompat threadsSwitch;
    private SwitchCompat psxDpadSwitch;
    private android.view.View psxDpadContainer;
    private SwitchCompat customTabsSwitch;
    private android.view.View customTabsContainer;

    // Controller ports configuration (all consoles)
    private android.widget.Spinner controllerPortSpinner1;
    private android.widget.Spinner controllerPortSpinner2;
    private android.widget.Spinner controllerPortSpinner3;
    private android.widget.Spinner controllerPortSpinner4;
    private android.view.View controllerPortsContainer;

    // N64 controller extension settings
    private android.widget.Spinner n64PakSpinner1;
    private android.widget.Spinner n64PakSpinner2;
    private android.widget.Spinner n64PakSpinner3;
    private android.widget.Spinner n64PakSpinner4;
    private android.view.View n64PakContainer;

    // Core specific options
    private android.view.View n64CoreOptions;
    private android.widget.Spinner n64ResolutionSpinner;
    private android.widget.Spinner n64AntiAliasingSpinner;
    private androidx.appcompat.widget.SwitchCompat n64BilinearSwitch;

    private android.view.View psxCoreOptions;
    private android.widget.Spinner psxResolutionSpinner;
    private androidx.appcompat.widget.SwitchCompat psxTextureFilteringSwitch;
    private androidx.appcompat.widget.SwitchCompat psxDitheringSwitch;

    private android.view.View snesCoreOptions;
    private android.widget.Spinner snesBlendModeSpinner;
    private androidx.appcompat.widget.SwitchCompat snesHiResSwitch;

    private android.widget.TextView noCoreOptionsText;
    private SeekBar touchScaleSeekBar;
    private SeekBar touchAlphaSeekBar;
    private TextView touchScaleValue;
    private TextView touchAlphaValue;
    private MaterialButton saveButton;
    private MaterialButton editHtmlButton;
    private MaterialButton resetNativeGamePadButton;

    // Available cores per console
    private static final String[] NES_CORES = { "fceumm", "nestopia" };
    private static final String[] SNES_CORES = { "snes9x" };
    private static final String[] N64_CORES = { "parallel_n64", "mupen64plus_next" };
    private static final String[] GENESIS_CORES = { "genesis_plus_gx", "picodrive" };
    private static final String[] GBA_CORES = { "mgba", "vba_next" };
    private static final String[] GB_CORES = { "gambatte", "sameboy" };
    private static final String[] GBC_CORES = { "gambatte", "sameboy" };
    private static final String[] NDS_CORES = { "desmume", "melonds" };
    private static final String[] PS1_CORES = { "pcsx_rearmed", "beetle_psx" };
    private static final String[] ARCADE_CORES = { "fbalpha2012", "mame2003_plus" };

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

        // Performance settings
        threadsSwitch = findViewById(R.id.threadsSwitch);

        // PSX-specific settings
        psxDpadSwitch = findViewById(R.id.psxDpadSwitch);
        psxDpadContainer = findViewById(R.id.psxDpadContainer);

        // Show D-Pad option for PSX and PSP (both support analog/digital controls)
        if (currentConsole.equals("psx") || currentConsole.equals("ps1") || currentConsole.equals("playstation")
                || currentConsole.equals("psp")) {
            psxDpadContainer.setVisibility(android.view.View.VISIBLE);
        } else {
            psxDpadContainer.setVisibility(android.view.View.GONE);
        }

        // Custom Tabs option (for SharedArrayBuffer support)
        customTabsSwitch = findViewById(R.id.customTabsSwitch);
        customTabsContainer = findViewById(R.id.customTabsContainer);

        // Controller ports configuration (all consoles)
        controllerPortSpinner1 = findViewById(R.id.controllerPortSpinner1);
        controllerPortSpinner2 = findViewById(R.id.controllerPortSpinner2);
        controllerPortSpinner3 = findViewById(R.id.controllerPortSpinner3);
        controllerPortSpinner4 = findViewById(R.id.controllerPortSpinner4);
        controllerPortsContainer = findViewById(R.id.controllerPortsContainer);

        // Setup controller ports spinners
        setupControllerPortsSpinners();

        // N64 Controller Pak settings
        n64PakSpinner1 = findViewById(R.id.n64PakSpinner1);
        n64PakSpinner2 = findViewById(R.id.n64PakSpinner2);
        n64PakSpinner3 = findViewById(R.id.n64PakSpinner3);
        n64PakSpinner4 = findViewById(R.id.n64PakSpinner4);
        n64PakContainer = findViewById(R.id.n64PakContainer);

        // Core specific options
        n64CoreOptions = findViewById(R.id.n64CoreOptions);
        n64ResolutionSpinner = findViewById(R.id.n64ResolutionSpinner);
        n64AntiAliasingSpinner = findViewById(R.id.n64AntiAliasingSpinner);
        n64BilinearSwitch = findViewById(R.id.n64BilinearSwitch);

        psxCoreOptions = findViewById(R.id.psxCoreOptions);
        psxResolutionSpinner = findViewById(R.id.psxResolutionSpinner);
        psxTextureFilteringSwitch = findViewById(R.id.psxTextureFilteringSwitch);
        psxDitheringSwitch = findViewById(R.id.psxDitheringSwitch);

        snesCoreOptions = findViewById(R.id.snesCoreOptions);
        snesBlendModeSpinner = findViewById(R.id.snesBlendModeSpinner);
        snesHiResSwitch = findViewById(R.id.snesHiResSwitch);

        noCoreOptionsText = findViewById(R.id.noCoreOptionsText);

        // Setup core specific options visibility
        setupCoreOptionsVisibility();

        // Show N64 Pak options only for N64
        if (currentConsole.equals("n64")) {
            n64PakContainer.setVisibility(android.view.View.VISIBLE);

            // Setup spinners with Pak options
            String[] pakOptions = { "🎯 Controller Pak (Memory)", "🔊 Rumble Pak (Vibration)",
                    "🎮 Transfer Pak (Game Boy)" };
            ThemeManager themeManager = ThemeManager.getInstance(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                    pakOptions) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, convertView, parent);
                    view.setTextColor(themeManager.getPrimaryColor(ConsoleConfigActivity.this));
                    view.setTextSize(12);
                    view.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                    view.setTextColor(android.graphics.Color.WHITE);
                    view.setBackgroundColor(themeManager.getHeaderBackgroundColor(ConsoleConfigActivity.this));
                    view.setTextSize(12);
                    view.setTypeface(android.graphics.Typeface.MONOSPACE);
                    view.setPadding(16, 12, 16, 12);
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            n64PakSpinner1.setAdapter(adapter);
            n64PakSpinner2.setAdapter(adapter);
            n64PakSpinner3.setAdapter(adapter);
            n64PakSpinner4.setAdapter(adapter);
        } else {
            n64PakContainer.setVisibility(android.view.View.GONE);
        }

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
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        touchAlphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = progress / 10.0f; // 0.0 to 1.0
                touchAlphaValue.setText(String.format("%.1f", alpha));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Save button
        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveConfiguration());

        // Edit HTML button
        editHtmlButton = findViewById(R.id.editHtmlButton);
        editHtmlButton.setOnClickListener(v -> openHtmlEditor());

        // Reset Native GamePad button
        resetNativeGamePadButton = findViewById(R.id.resetNativeGamePadButton);
        resetNativeGamePadButton.setOnClickListener(v -> resetNativeGamePad());
    }

    private String getDefaultPresetForConsole(String console) {
        switch (console.toLowerCase()) {
            case "nes":
                return "NES Native (Default)";
            case "snes":
                return "SNES Native (Default)";
            case "n64":
                return "N64 Native (Default)";
            case "megadrive":
            case "genesis":
                return "Mega Drive 6-Button (Default)";
            case "psx":
            case "ps1":
            case "playstation":
                return "PSX DualShock (Analog)";
            case "gb":
                return "Game Boy Native (Default)";
            case "gbc":
                return "GBC Native (Default)";
            case "gba":
                return "GBA Native (Default)";
            case "psp":
                return "PSP Analog (Default)";
            default:
                return "Default (Auto-detect)";
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfiguration() {
        String prefix = currentConsole + "_";

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

        // Load controller ports settings (default: -1 = Auto)
        // Note: Les valeurs sont sauvegardées comme IDs Libretro, mais on doit trouver
        // la position dans le spinner
        if (controllerPortSpinner1 != null) {
            int port1Type = prefs.getInt("controller_port_" + currentConsole + "_port0", -1);
            int port1Position = getControllerTypePosition(port1Type);
            controllerPortSpinner1.setSelection(port1Position);
        }
        if (controllerPortSpinner2 != null) {
            int port2Type = prefs.getInt("controller_port_" + currentConsole + "_port1", -1);
            int port2Position = getControllerTypePosition(port2Type);
            controllerPortSpinner2.setSelection(port2Position);
        }
        if (controllerPortSpinner3 != null) {
            int port3Type = prefs.getInt("controller_port_" + currentConsole + "_port2", -1);
            int port3Position = getControllerTypePosition(port3Type);
            controllerPortSpinner3.setSelection(port3Position);
        }
        if (controllerPortSpinner4 != null) {
            int port4Type = prefs.getInt("controller_port_" + currentConsole + "_port3", -1);
            int port4Position = getControllerTypePosition(port4Type);
            controllerPortSpinner4.setSelection(port4Position);
        }

        // Load N64 Pak settings (default: Controller Pak = 0)
        if (n64PakSpinner1 != null)
            n64PakSpinner1.setSelection(prefs.getInt(prefix + "pak_port1", 0));
        if (n64PakSpinner2 != null)
            n64PakSpinner2.setSelection(prefs.getInt(prefix + "pak_port2", 0));
        if (n64PakSpinner3 != null)
            n64PakSpinner3.setSelection(prefs.getInt(prefix + "pak_port3", 0));
        if (n64PakSpinner4 != null)
            n64PakSpinner4.setSelection(prefs.getInt(prefix + "pak_port4", 0));

        // Load core specific settings
        // Clés: n64_resolution, n64_antialiasing, n64_bilinear (sans redondance)
        if (n64ResolutionSpinner != null)
            n64ResolutionSpinner.setSelection(prefs.getInt(prefix + "resolution", 0));
        if (n64AntiAliasingSpinner != null)
            n64AntiAliasingSpinner.setSelection(prefs.getInt(prefix + "antialiasing", 0));
        if (n64BilinearSwitch != null)
            n64BilinearSwitch.setChecked(prefs.getBoolean(prefix + "bilinear", false));

        // Load core specific settings
        // Clés: psx_resolution, psx_texture_filtering, psx_dithering (sans redondance)
        if (psxResolutionSpinner != null)
            psxResolutionSpinner.setSelection(prefs.getInt(prefix + "resolution", 0));
        if (psxTextureFilteringSwitch != null)
            psxTextureFilteringSwitch.setChecked(prefs.getBoolean(prefix + "texture_filtering", true));
        if (psxDitheringSwitch != null)
            psxDitheringSwitch.setChecked(prefs.getBoolean(prefix + "dithering", true));

        // Clés: snes_blend_mode, snes_hires (sans redondance)
        if (snesBlendModeSpinner != null)
            snesBlendModeSpinner.setSelection(prefs.getInt(prefix + "blend_mode", 0));
        if (snesHiResSwitch != null)
            snesHiResSwitch.setChecked(prefs.getBoolean(prefix + "hires", false));

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

        // Save controller ports settings
        // Note: Sauvegarder les IDs Libretro, pas les positions spinner
        if (controllerPortSpinner1 != null) {
            int port1Id = getControllerTypeId(controllerPortSpinner1.getSelectedItemPosition());
            editor.putInt("controller_port_" + currentConsole + "_port0", port1Id);
        }
        if (controllerPortSpinner2 != null) {
            int port2Id = getControllerTypeId(controllerPortSpinner2.getSelectedItemPosition());
            editor.putInt("controller_port_" + currentConsole + "_port1", port2Id);
        }
        if (controllerPortSpinner3 != null) {
            int port3Id = getControllerTypeId(controllerPortSpinner3.getSelectedItemPosition());
            editor.putInt("controller_port_" + currentConsole + "_port2", port3Id);
        }
        if (controllerPortSpinner4 != null) {
            int port4Id = getControllerTypeId(controllerPortSpinner4.getSelectedItemPosition());
            editor.putInt("controller_port_" + currentConsole + "_port3", port4Id);
        }

        // Save N64 Pak settings
        if (n64PakSpinner1 != null)
            editor.putInt(prefix + "pak_port1", n64PakSpinner1.getSelectedItemPosition());
        if (n64PakSpinner2 != null)
            editor.putInt(prefix + "pak_port2", n64PakSpinner2.getSelectedItemPosition());
        if (n64PakSpinner3 != null)
            editor.putInt(prefix + "pak_port3", n64PakSpinner3.getSelectedItemPosition());
        if (n64PakSpinner4 != null)
            editor.putInt(prefix + "pak_port4", n64PakSpinner4.getSelectedItemPosition());

        // Save core specific settings
        // Clés: n64_resolution, n64_antialiasing, n64_bilinear (sans redondance)
        if (n64ResolutionSpinner != null)
            editor.putInt(prefix + "resolution", n64ResolutionSpinner.getSelectedItemPosition());
        if (n64AntiAliasingSpinner != null)
            editor.putInt(prefix + "antialiasing", n64AntiAliasingSpinner.getSelectedItemPosition());
        if (n64BilinearSwitch != null)
            editor.putBoolean(prefix + "bilinear", n64BilinearSwitch.isChecked());

        // Save core specific settings
        // Clés: psx_resolution, psx_texture_filtering, psx_dithering (sans redondance)
        if (psxResolutionSpinner != null)
            editor.putInt(prefix + "resolution", psxResolutionSpinner.getSelectedItemPosition());
        if (psxTextureFilteringSwitch != null)
            editor.putBoolean(prefix + "texture_filtering", psxTextureFilteringSwitch.isChecked());
        if (psxDitheringSwitch != null)
            editor.putBoolean(prefix + "dithering", psxDitheringSwitch.isChecked());

        // Clés: snes_blend_mode, snes_hires (sans redondance)
        if (snesBlendModeSpinner != null)
            editor.putInt(prefix + "blend_mode", snesBlendModeSpinner.getSelectedItemPosition());
        if (snesHiResSwitch != null)
            editor.putBoolean(prefix + "hires", snesHiResSwitch.isChecked());

        // Save control settings
        float touchScale = 0.5f + (touchScaleSeekBar.getProgress() / 20.0f) * 1.5f;
        editor.putFloat(prefix + "touch_scale", touchScale);

        float touchAlpha = touchAlphaSeekBar.getProgress() / 10.0f;
        editor.putFloat(prefix + "touch_alpha", touchAlpha);

        editor.apply();

        // Log saved values
        System.out.println("===== Advanced Configuration Saved =====");
        System.out.println("Console: " + currentConsole);
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
                    android.util.Log.d("ConsoleConfig",
                            "Loaded core from console.json for " + console + ": " + defaultCore);
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
            if (c == '[' || c == '{')
                depth++;
            else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0)
                    return i;
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
                "                { type: \"dpad\", location: \"left\", left: \"10%\", top: \"50%\", joystickInput: false, inputValues: [4, 5, 6, 7] },\n"
                +
                "                // Bouton A\n" +
                "                { type: \"button\", text: \"A\", id: \"btn_a\", location: \"right\", right: 80, top: 120, bold: true, fontSize: 24, input_value: 8 },\n"
                +
                "                // Bouton B\n" +
                "                { type: \"button\", text: \"B\", id: \"btn_b\", location: \"right\", right: 20, top: 80, bold: true, fontSize: 24, input_value: 0 },\n"
                +
                "                // Bouton C\n" +
                "                { type: \"button\", text: \"C\", id: \"btn_c\", location: \"right\", right: 140, top: 80, bold: true, fontSize: 24, input_value: 9 },\n"
                +
                "                // Bouton X\n" +
                "                { type: \"button\", text: \"X\", id: \"btn_x\", location: \"right\", right: 80, top: 40, bold: true, fontSize: 20, input_value: 1 },\n"
                +
                "                // Bouton Y\n" +
                "                { type: \"button\", text: \"Y\", id: \"btn_y\", location: \"right\", right: 20, top: 10, bold: true, fontSize: 20, input_value: 10 },\n"
                +
                "                // Bouton Z\n" +
                "                { type: \"button\", text: \"Z\", id: \"btn_z\", location: \"right\", right: 140, top: 10, bold: true, fontSize: 20, input_value: 11 },\n"
                +
                "                // Start\n" +
                "                { type: \"button\", text: \"START\", id: \"start\", location: \"center\", left: 60, fontSize: 15, block: true, input_value: 3 },\n"
                +
                "                // Select/Mode\n" +
                "                { type: \"button\", text: \"SELECT\", id: \"select\", location: \"center\", left: -5, fontSize: 15, block: true, input_value: 2 }\n"
                +
                "            ]";
    }

    private String getSnesPreset() {
        return "[\n" +
                "                // D-Pad\n" +
                "                { type: \"dpad\", location: \"left\", left: \"10%\", top: \"50%\", joystickInput: false, inputValues: [4, 5, 6, 7] },\n"
                +
                "                // Boutons face (Y, X, B, A)\n" +
                "                { type: \"button\", text: \"Y\", id: \"y\", location: \"right\", right: 40, top: 40, bold: true, fontSize: 24, input_value: 9 },\n"
                +
                "                { type: \"button\", text: \"X\", id: \"x\", location: \"right\", right: 100, top: 80, bold: true, fontSize: 24, input_value: 1 },\n"
                +
                "                { type: \"button\", text: \"B\", id: \"b\", location: \"right\", right: 100, top: 120, bold: true, fontSize: 24, input_value: 8 },\n"
                +
                "                { type: \"button\", text: \"A\", id: \"a\", location: \"right\", right: 160, top: 80, bold: true, fontSize: 24, input_value: 0 },\n"
                +
                "                // L/R triggers\n" +
                "                { type: \"button\", text: \"L\", id: \"l\", location: \"left\", left: 20, top: 10, fontSize: 18, input_value: 10 },\n"
                +
                "                { type: \"button\", text: \"R\", id: \"r\", location: \"right\", right: 20, top: 10, fontSize: 18, input_value: 11 },\n"
                +
                "                // Start/Select\n" +
                "                { type: \"button\", text: \"START\", id: \"start\", location: \"center\", left: 60, fontSize: 14, block: true, input_value: 3 },\n"
                +
                "                { type: \"button\", text: \"SELECT\", id: \"select\", location: \"center\", left: -5, fontSize: 14, block: true, input_value: 2 }\n"
                +
                "            ]";
    }

    private void openHtmlEditor() {
        String htmlPath = "/storage/emulated/0/RetroPlay-Files/sites/emulator.html";
        java.io.File htmlFile = new java.io.File(htmlPath);

        if (!htmlFile.exists()) {
            android.widget.Toast.makeText(this, "emulator.html not found in storage", android.widget.Toast.LENGTH_LONG)
                    .show();
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
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(
                            CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("HTML Path", htmlPath);
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(this, "Path copied to clipboard", android.widget.Toast.LENGTH_SHORT)
                            .show();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    /**
     * Réinitialiser les paramètres gamepad natifs pour cette console
     */
    private void resetNativeGamePad() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Reset Native GamePad")
                .setMessage("This will reset the gamepad settings for " + currentConsole.toUpperCase()
                        + " in NATIVE mode (NativeCompose) to DEFAULT.\n\n" +
                        "This does NOT affect EmulatorJS (WebView/WASM) mode.\n\nContinue?")
                .setPositiveButton("RESET", (dialog, which) -> {
                    // Supprimer les préférences gamepad pour cette console
                    SharedPreferences gamepadPrefs = getSharedPreferences("compose_gamepad_settings",
                            Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = gamepadPrefs.edit();

                    // Supprimer toutes les clés liées à cette console
                    // Layout variant (DEFAULT, RETROARCH, etc.)
                    editor.remove("gamepad_" + currentConsole + "_variant");

                    // Overlay preferences (RetroArch)
                    editor.remove("overlay_" + currentConsole + "_enabled");
                    editor.remove("overlay_" + currentConsole + "_name");
                    editor.remove("overlay_" + currentConsole + "_layout_landscape");
                    editor.remove("overlay_" + currentConsole + "_layout_portrait");
                    editor.remove("overlay_" + currentConsole + "_auto_rotate");

                    // Settings gamepad (scale, rotation, margins)
                    editor.remove("gamepad_" + currentConsole + "_settings_scale");
                    editor.remove("gamepad_" + currentConsole + "_settings_rotation");
                    editor.remove("gamepad_" + currentConsole + "_settings_marginX");
                    editor.remove("gamepad_" + currentConsole + "_settings_marginY");

                    editor.apply();

                    android.widget.Toast.makeText(this,
                            "Native gamepad reset to DEFAULT for " + currentConsole.toUpperCase(),
                            android.widget.Toast.LENGTH_LONG).show();

                    android.util.Log.i("ConsoleConfig", "Reset native gamepad settings for " + currentConsole);
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

    private void setupControllerPortsSpinners() {
        // Controller types disponibles (IDs Libretro standards)
        // Position 0 = Auto (-1), 1 = None (0), 2 = Joypad (1), 3 = Lightgun (4), 4 =
        // Pointer (6), 5 = Zapper (258)
        String[] controllerTypes = {
                "Auto (Default)",
                "None",
                "Joypad",
                "Lightgun",
                "Pointer",
                "Zapper (NES)"
        };

        ThemeManager themeManager = ThemeManager.getInstance(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                controllerTypes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(themeManager.getPrimaryColor(ConsoleConfigActivity.this));
                view.setTextSize(12);
                view.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(android.graphics.Color.WHITE);
                view.setBackgroundColor(themeManager.getHeaderBackgroundColor(ConsoleConfigActivity.this));
                view.setTextSize(12);
                view.setTypeface(android.graphics.Typeface.MONOSPACE);
                view.setPadding(16, 12, 16, 12);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        if (controllerPortSpinner1 != null)
            controllerPortSpinner1.setAdapter(adapter);
        if (controllerPortSpinner2 != null)
            controllerPortSpinner2.setAdapter(adapter);
        if (controllerPortSpinner3 != null)
            controllerPortSpinner3.setAdapter(adapter);
        if (controllerPortSpinner4 != null)
            controllerPortSpinner4.setAdapter(adapter);
    }

    /**
     * Convertir position spinner vers ID Libretro
     */
    private int getControllerTypeId(int spinnerPosition) {
        // Position 0 = Auto (-1), 1 = None (0), 2 = Joypad (1), 3 = Lightgun (4), 4 =
        // Pointer (6), 5 = Zapper (258)
        switch (spinnerPosition) {
            case 0:
                return -1; // Auto
            case 1:
                return 0; // None
            case 2:
                return 1; // Joypad
            case 3:
                return 4; // Lightgun
            case 4:
                return 6; // Pointer
            case 5:
                return 258; // Zapper (FCEUmm)
            default:
                return -1; // Auto par défaut
        }
    }

    /**
     * Convertir ID Libretro vers position spinner
     */
    private int getControllerTypePosition(int controllerId) {
        // Position 0 = Auto (-1), 1 = None (0), 2 = Joypad (1), 3 = Lightgun (4), 4 =
        // Pointer (6), 5 = Zapper (258)
        switch (controllerId) {
            case -1:
                return 0; // Auto
            case 0:
                return 1; // None
            case 1:
                return 2; // Joypad
            case 4:
                return 3; // Lightgun
            case 6:
                return 4; // Pointer
            case 258:
                return 5; // Zapper
            default:
                return 0; // Auto par défaut
        }
    }

    private void setupCoreOptionsVisibility() {
        // Hide all native options by default
        if (controllerPortsContainer != null)
            controllerPortsContainer.setVisibility(android.view.View.VISIBLE); // Toujours visible
        if (n64PakContainer != null)
            n64PakContainer.setVisibility(android.view.View.GONE);
        if (psxDpadContainer != null)
            psxDpadContainer.setVisibility(android.view.View.GONE);
        if (n64CoreOptions != null)
            n64CoreOptions.setVisibility(android.view.View.GONE);
        if (psxCoreOptions != null)
            psxCoreOptions.setVisibility(android.view.View.GONE);
        if (snesCoreOptions != null)
            snesCoreOptions.setVisibility(android.view.View.GONE);
        if (noCoreOptionsText != null)
            noCoreOptionsText.setVisibility(android.view.View.VISIBLE);

        // Show specific options based on console
        if (currentConsole.equals("n64")) {
            if (n64PakContainer != null)
                n64PakContainer.setVisibility(android.view.View.VISIBLE);
            if (n64CoreOptions != null)
                n64CoreOptions.setVisibility(android.view.View.VISIBLE);
            if (noCoreOptionsText != null)
                noCoreOptionsText.setVisibility(android.view.View.GONE);

            // Setup N64 options
            String[] resolutionOptions = { "320x240 (Native)", "640x480 (2x)", "960x720 (3x)", "1280x960 (4x)" };
            setupSpinner(n64ResolutionSpinner, resolutionOptions);

            String[] aaOptions = { "Off", "2x MSAA", "4x MSAA", "8x MSAA" };
            setupSpinner(n64AntiAliasingSpinner, aaOptions);

        } else if (currentConsole.equals("psx") || currentConsole.equals("ps1")
                || currentConsole.equals("playstation")) {
            if (psxDpadContainer != null)
                psxDpadContainer.setVisibility(android.view.View.VISIBLE);
            if (psxCoreOptions != null)
                psxCoreOptions.setVisibility(android.view.View.VISIBLE);
            if (noCoreOptionsText != null)
                noCoreOptionsText.setVisibility(android.view.View.GONE);

            // Setup PSX options
            String[] resolutionOptions = { "1x (240p)", "2x (480p)", "4x (960p)", "8x (1920p)" };
            setupSpinner(psxResolutionSpinner, resolutionOptions);

        } else if (currentConsole.equals("snes")) {
            if (snesCoreOptions != null)
                snesCoreOptions.setVisibility(android.view.View.VISIBLE);
            if (noCoreOptionsText != null)
                noCoreOptionsText.setVisibility(android.view.View.GONE);

            // Setup SNES options
            String[] blendOptions = { "None", "Merge", "Additive", "Subtractive" };
            setupSpinner(snesBlendModeSpinner, blendOptions);
        }
    }

    private void setupSpinner(android.widget.Spinner spinner, String[] options) {
        if (spinner == null)
            return;

        ThemeManager themeManager = ThemeManager.getInstance(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(themeManager.getPrimaryColor(ConsoleConfigActivity.this));
                view.setTextSize(12);
                view.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(android.graphics.Color.WHITE);
                view.setBackgroundColor(themeManager.getMediumColor(ConsoleConfigActivity.this));
                view.setTextSize(12);
                view.setTypeface(android.graphics.Typeface.MONOSPACE);
                view.setPadding(16, 12, 16, 12);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
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
