# Core Options vs Global Settings Architecture

## Résumé
RetroPlay suit l'architecture RetroArch pour la gestion de la configuration :
- **Core Options** : partagées entre tous les jeux du même core
- **Global Settings** : peuvent être overridées per-game (Run-Ahead, Rewind, etc.)

---

## 1. Core Options (partagées par core)

### Emplacement
```
/storage/emulated/0/RetroPlay-Data/config/{CoreName}/{CoreName}.cfg
```

### Exemples
```
/storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg
/storage/emulated/0/RetroPlay-Data/config/Genesis_Plus_GX/Genesis_Plus_GX.cfg
/storage/emulated/0/RetroPlay-Data/config/Mupen64Plus/Mupen64Plus.cfg
```

### Format
```ini
# FCEUmm Core Options (NES)
fceumm_zapper_mode = "touchscreen"
fceumm_palette = "default"
fceumm_overscan_v_top = "8"
fceumm_overscan_v_bottom = "8"
fceumm_show_crosshair = "enabled"
fceumm_zapper_tolerance = "20"
```

### Caractéristiques
- **Partagées** : tous les jeux NES partagent les mêmes Core Options
- **Spécifiques au core** : options internes au core (palette, overclock, etc.)
- **Gestion** : via `CoreConfigManager.kt`
- **Équivalent RetroArch** : `.opt` files

### Exemples de Core Options
| Core | Options typiques |
|------|-----------------|
| **FCEUmm** | `fceumm_zapper_mode`, `fceumm_palette`, `fceumm_overscan_v_top` |
| **Genesis Plus GX** | `genesis_plus_gx_ym2413`, `genesis_plus_gx_aspect_ratio` |
| **Mupen64Plus** | `mupen64plus-rdp-plugin`, `mupen64plus-rsp-plugin` |
| **MAME 2003-Plus** | `mame2003-plus_frameskip`, `mame2003-plus_dcs-speedhack` |

---

## 2. Global Settings (per-game overrides optionnels)

### Emplacement
```
/storage/emulated/0/RetroPlay-Data/config/retroplay.cfg (global)
/storage/emulated/0/RetroPlay-Data/config/games/{GameCRC}.cfg (per-game override)
```

### Format Global
```ini
# retroplay.cfg (settings globaux pour tous les jeux)
run_ahead_enabled = "false"
run_ahead_frames = "1"
rewind_enabled = "false"
rewind_granularity = "1"
audio_latency = "64"
video_vsync = "true"
smart_config_enabled = "false"
smart_config_auto_runahead = "false"
smart_config_auto_rewind = "false"
smart_config_auto_overlay = "false"
smart_config_show_osd = "true"
```

### Format Per-Game Override (optionnel)
```ini
# games/D445F698.cfg (Super Mario Bros - override)
run_ahead_enabled = "true"
run_ahead_frames = "2"
rewind_enabled = "false"
```

### Caractéristiques
- **Globales** : par défaut, tous les jeux héritent de `retroplay.cfg`
- **Overridables** : chaque jeu peut avoir son propre `.cfg` qui override
- **Optionnelles** : si pas de fichier per-game, utilise le global
- **Gestion** : via `RetroPlayConfigManager.kt`
- **Équivalent RetroArch** : `.cfg` files (global + per-game)

### Exemples de Global Settings
| Catégorie | Settings |
|-----------|----------|
| **Performance** | `run_ahead_enabled`, `run_ahead_frames`, `run_ahead_secondary_instance` |
| **Gameplay** | `rewind_enabled`, `rewind_granularity`, `rewind_buffer_size_mb` |
| **Audio** | `audio_latency`, `audio_sync`, `audio_max_timing_skew` |
| **Video** | `video_vsync`, `video_hard_sync`, `video_frame_delay` |
| **Smart Config** | `smart_config_enabled`, `smart_config_auto_*`, `smart_config_show_osd` |

---

## 3. Comparaison avec RetroArch

### RetroArch
```
/storage/emulated/0/RetroArch/config/
├── FCEUmm/
│   └── FCEUmm.opt                      # Core Options (partagées)
├── fceumm_libretro_android.cfg         # Global settings (default)
├── fceumm_libretro_android-1.cfg       # Per-game override (Duck Hunt)
├── fceumm_libretro_android-2.cfg       # Per-game override (Super Mario)
└── retroarch.cfg                        # Global config
```

### RetroPlay
```
/storage/emulated/0/RetroPlay-Data/config/
├── FCEUmm/
│   └── FCEUmm.cfg                      # Core Options (partagées)
├── retroplay.cfg                        # Global settings (default)
└── games/
    ├── D445F698.cfg                    # Per-game override (Super Mario)
    └── 4644085E.cfg                    # Per-game override (Duck Hunt)
```

### Différences
| Aspect | RetroArch | RetroPlay |
|--------|-----------|-----------|
| **Core Options** | `.opt` files | `.cfg` files |
| **Global Settings** | `retroarch.cfg` | `retroplay.cfg` |
| **Per-Game Override** | `{core}-{id}.cfg` | `games/{CRC}.cfg` |
| **Naming** | Core + ID incrémental | CRC du ROM |

---

## 4. Pourquoi cette architecture ?

### Avantages du modèle "Core Options partagées"
1. **Simplicité** : une seule config par core
2. **Cohérence** : tous les jeux NES partagent la même palette, overscan, etc.
3. **Maintenance** : changer `fceumm_palette` affecte tous les jeux NES
4. **Alignement RetroArch** : compatible avec les configs RetroArch

### Avantages des "Global Settings per-game"
1. **Flexibilité** : Run-Ahead peut varier par jeu (genre-based)
2. **Performance** : jeux d'action = 4 frames, RPG = 1 frame
3. **Personnalisation** : l'utilisateur peut optimiser par jeu
4. **Smart Config** : permet des recommandations intelligentes par jeu

### Cas limites
**Q : Et si Duck Hunt veut `fceumm_zapper_mode = "touchscreen"` mais un autre jeu veut `"lightgun"` ?**
- **R :** L'utilisateur doit changer manuellement la Core Option via Core Options Dialog
- Alternative avancée : permettre des Core Options per-game (non implémenté pour simplicité)

**Q : Et si je veux des Core Options différentes pour chaque jeu ?**
- **R :** Cela nécessiterait une architecture différente (chaque jeu = sa propre `.cfg`)
- Pas implémenté car la majorité des Core Options sont globales au core
- Les rares exceptions (ex: Zapper) peuvent être gérées manuellement

---

## 5. Smart Config et Recommandations

### Principe
Smart Config utilise les **métadonnées du jeu** (genre, développeur, année) pour **recommander** des Global Settings optimales, **sans modifier les Core Options**.

### Exemple : Super Mario Bros.
```
CRC: D445F698
Genre: Platform
Developer: Nintendo
Year: 1985
```

**Smart Config recommande :**
```ini
# Global Settings (can be overridden per-game)
run_ahead_enabled = "true"
run_ahead_frames = "2"        # Platformers = 2 frames
rewind_enabled = "false"      # Platformers don't need rewind
```

**Core Options (restent inchangées) :**
```ini
# Shared across all NES games
fceumm_zapper_mode = "touchscreen"
fceumm_palette = "default"
fceumm_overscan_v_top = "8"
```

### Pourquoi Smart Config ne touche pas aux Core Options ?
1. Core Options sont **spécifiques au core**, pas au jeu
2. Exemple : `fceumm_palette` s'applique à **tous** les jeux NES
3. Smart Config est **basé sur le jeu** (genre, metadata)
4. Modifier une Core Option affecterait **tous** les jeux du même core

---

## 6. Implémentation Actuelle

### Classes
| Classe | Rôle | Gère |
|--------|------|------|
| **`CoreConfigManager.kt`** | Gère Core Options (partagées) | `.cfg` files in `config/{CoreName}/` |
| **`RetroPlayConfigManager.kt`** | Gère Global Settings (global + per-game) | `retroplay.cfg` + `games/{CRC}.cfg` |
| **`SmartConfigManager.kt`** | Génère recommandations basées sur metadata | Recommandations uniquement |
| **`DatabaseManager.kt`** | Charge metadata (CRC, genre, dev, year) | `.rdb` database files |

### Flux de configuration au lancement d'un jeu

```
1. Charger Core Options (partagées)
   CoreConfigManager.loadConfig(context, "FCEUmm")
   → /storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg
   
2. Charger Global Settings (global)
   RetroPlayConfigManager.loadConfig()
   → /storage/emulated/0/RetroPlay-Data/config/retroplay.cfg
   
3. Charger Per-Game Override (si existe)
   RetroPlayConfigManager.loadGameConfig(gameCRC)
   → /storage/emulated/0/RetroPlay-Data/config/games/D445F698.cfg
   
4. Lookup Metadata
   DatabaseManager.getGameInfo("nes", "D445F698")
   → GameInfo(name="Super Mario Bros.", genre="Platform", ...)
   
5. Générer Recommandations Smart Config
   SmartConfigManager.getOptimalConfig(gameInfo)
   → Recommendations(runAheadFrames=2, rewindEnabled=false, ...)
   
6. Afficher dans GameInfoDialog
   "💡 Recommended: Run-Ahead 2 frames (Platformer)"
   
7. Appliquer Core Options au core
   retroView.setVariables(coreOptions)
   
8. Appliquer Global Settings
   if (config.runAheadEnabled) { enableRunAhead(config.runAheadFrames) }
```

---

## 7. Futur : Per-Game Core Options ?

### Scénario
Si l'utilisateur veut vraiment des Core Options différentes par jeu :
```
/storage/emulated/0/RetroPlay-Data/config/
├── FCEUmm/
│   ├── FCEUmm.cfg               # Default (shared)
│   └── games/
│       ├── D445F698.cfg         # Super Mario (override)
│       └── 4644085E.cfg         # Duck Hunt (override)
```

### Implémentation
```kotlin
// Load core options with per-game override
fun loadCoreOptionsWithOverride(context: Context, coreName: String, gameCRC: String?): Map<String, String> {
    // 1. Load shared core options
    val sharedOptions = loadConfig(context, coreName)
    
    // 2. Load per-game override (if exists)
    if (gameCRC != null) {
        val gameOverride = loadGameCoreOptions(context, coreName, gameCRC)
        return sharedOptions + gameOverride  // Merge with override priority
    }
    
    return sharedOptions
}
```

### Avantages
- Flexibilité maximale
- Duck Hunt peut avoir `fceumm_zapper_mode = "touchscreen"`
- Super Mario peut avoir `fceumm_palette = "smooth"`

### Inconvénients
- Plus complexe
- Diverge de RetroArch
- Maintenance difficile (2 niveaux de config)

**Décision : Non implémenté pour l'instant (garder simplicité RetroArch)**

---

## Résumé

✅ **RetroPlay suit Option 1 (architecture RetroArch)**
- Core Options : partagées par core
- Global Settings : overridables per-game
- Smart Config : recommandations basées sur metadata
- Simple, maintenable, aligné avec RetroArch

📊 **Smart Config = Recommandations SEULEMENT**
- Affiche dans GameInfoDialog
- N'applique PAS automatiquement (sauf si toggle activé)
- Basé sur genre/metadata du jeu
- L'utilisateur décide d'appliquer ou non

