# AUDIT COMPLET DE c:\repos

**Date:** 2025-11-03  
**Total:** 16 répertoires explorés  
**Objectif:** Source de vérité pour toutes les implémentations RetroPlay

---

## STRUCTURE GLOBALE

```
c:\repos\
├── RetroArch-master/          ✅ Frontend principal LibRetro
├── libretro-fceumm-master/    ✅ Core NES (Zapper fix mousedata[3])
├── LibretroDroid/             ✅ Bibliothèque Android (base de RetroPlay)
├── LibretroDroid-master/      (clone duplicate)
├── Lemuroid/                  ✅ Émulateur Android (inspiration UX)
├── common-overlays-master/    ✅ Overlays officiels RetroArch
├── docs-master/               ✅ Documentation complète LibRetro
├── retroarch-assets-master/   ✅ Assets UI (icons, fonts, sounds)
├── retroarch-joypad-autoconfig-master/ ✅ Configs gamepad
├── libretro-database-master/  ✅ Métadonnées (cheats, thumbnails)
├── libretro-database/         (duplicate)
├── libretro-super-master/     ✅ Build system et scripts
├── libretro-common-master/    ✅ Bibliothèque partagée cores
├── info/                      ✅ Fichiers .info de tous les cores
├── glsl-shaders-master/       ✅ Shaders GLSL
└── slang-shaders-master/      ✅ Shaders Slang (modernes)
```

---

## 1. RetroArch-master (FRONTEND PRINCIPAL)

**Chemin:** `c:\repos\RetroArch-master`  
**Dernière modif:** 2025-08-18

### Fichiers critiques pour RetroPlay:

#### A. Configuration et defaults
- **`configuration.h`** (lignes 335-720)
  - Toutes les variables overlay: opacity, scale, offsets, sensitivity, lightgun, mouse
  - Types: `floats.input_overlay_*`, `uints.input_overlay_*`, `bools.input_overlay_*`
  
- **`config.def.h`** (lignes 580-648)
  - **VALEURS PAR DÉFAUT OFFICIELLES:**
    - `DEFAULT_INPUT_OVERLAY_OPACITY = 0.7f`
    - `DEFAULT_OVERLAY_DPAD_DIAGONAL_SENSITIVITY = 80`
    - `DEFAULT_OVERLAY_ABXY_DIAGONAL_SENSITIVITY = 50`
    - `DEFAULT_OVERLAY_HIDE_IN_MENU = true`
    - `DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY = 1`
    - `DEFAULT_INPUT_OVERLAY_MOUSE_HOLD_MSEC = 200`
  - **Utilisées pour corriger RetroPlay!**

#### B. Input système
- **`input/input_driver.c`** (8149 lignes)
  - **Lignes 2230-2268:** Calcul diagonal sensitivity (formules exactes)
  - **Lignes 2279-2343:** 8-way direction detection (eightway_state)
  - **Lignes 2358-2430:** Analog stick avec recenter zone
  - **Lignes 3153-3177:** Lightgun multi-touch (2/3/4 doigts)
  - **Lignes 5320-5340:** Hide in menu logic

- **`input/input_overlay.h`** (484 lignes)
  - Structures complètes: `overlay_desc`, `overlay`, `input_overlay`
  - Enums: `overlay_type`, `overlay_show_input_type`, `overlay_lightgun_action`
  - Constants: `OVERLAY_MAX_TOUCH = 16`, `OVERLAY_LIGHTGUN_TRIG_MAX_DELAY = 15`

- **`tasks/task_overlay.c`** (1112 lignes)
  - Chargement et parsing des fichiers .cfg
  - Gestion des images par bouton
  - Layouts landscape/portrait

#### C. Coordinate translation (CRUCIAL pour Zapper)
- **`gfx/video_driver.c`** — `video_driver_translate_coord_viewport()`
  - Conversion touch → game coordinates avec aspect ratio
  - Gestion letterboxing

### Utilité pour RetroPlay:
✅ **Utilisé activement:** Valeurs par défaut, formules mathématiques, logique hideInMenu  
✅ **Référence:** Pour toute implémentation overlay/input

---

## 2. libretro-fceumm-master (CORE NES)

**Chemin:** `c:\repos\libretro-fceumm-master`  
**Dernière modif:** 2025-11-02

### Fichiers critiques:

#### A. Zapper (LIGHTGUN NES)
- **`src/input/zapper.c`** (257 lignes)
  - **Lignes 66-113:** `ZapperFrapper()` — Détection lumière
    - `tolerance` (radius de détection, défaut 6px)
    - `CheckColor()` RGB > 300 = HIT
    - **FIX CRITIQUE:** `mousedata[3] = 0` (ligne ajoutée manuellement)
  
- **`src/drivers/libretro/libretro.c`** (2525 lignes)
  - **Lignes 87-97:** Variables crop overscan
    ```c
    static int crop_overscan_h_left;
    static int crop_overscan_h_right;
    static int crop_overscan_v_top;
    static int crop_overscan_v_bottom;
    ```
  - **Lignes 1719-1720:** Calcul dimensions avec crop
    ```c
    unsigned width  = NES_WIDTH  - crop_overscan_h_left - crop_overscan_h_right;
    unsigned height = NES_HEIGHT - crop_overscan_v_top - crop_overscan_v_bottom;
    ```
  - **Lignes 2442-2443:** Offsets pour conversion coordonnées
    ```c
    int offset_x = (crop_overscan_h_left * 0x120) - 1;
    int offset_y = (crop_overscan_v_top * 0x133) + 1;
    ```
  - **Lignes 2454-2455:** Conversion libretro → NES
    ```c
    mousedata[0] = ((mouse_x + (32767 + offset_x)) * max_width) / ((32767 + offset_x) * 2);
    mousedata[1] = ((mouse_y + (32767 + offset_y)) * max_height) / ((32767 + offset_y) * 2);
    ```

#### B. Core options
- **`src/drivers/libretro/libretro-core-options.h`**
  - `fceumm_zapper_mode = "touchscreen"` (vs "lightgun")
  - `fceumm_zapper_tolerance = "6"` (radius détection)
  - `fceumm_show_crosshair = "enabled"`
  - `fceumm_overscan_v_top/bottom = "8"`

### Utilité pour RetroPlay:
✅ **Core compilé utilisé dans RetroPlay:** `app/src/main/jniLibs/arm64-v8a/fceumm_libretro_android.so`  
✅ **Formules de conversion implémentées dans `handleZapperTouch()`**  
✅ **Lecture crop overscan depuis .cfg FCEUmm**

---

## 3. LibretroDroid (BIBLIOTHÈQUE ANDROID)

**Chemin:** `c:\repos\LibretroDroid` + `c:\repos\LibretroDroid-master`  
**Dernière modif:** 2025-10-18

### Différence avec RetroPlay:
RetroPlay utilise une **FORK personnalisée** de LibretroDroid avec:
- ✅ `getAspectRatio()` — AJOUTÉ par RetroPlay
- ✅ `getGameGeometryWidth/Height()` — AJOUTÉ par RetroPlay
- ✅ `onMouseButton()` — AJOUTÉ par RetroPlay pour Zapper
- ❌ LibretroDroid officiel: N'a PAS ces fonctions

### Structure LibretroDroid:
```cpp
libretrodroid/src/main/cpp/
├── libretrodroid.cpp       // Instance principale
├── input.cpp               // Gestion input (onMotionEvent, onKeyEvent)
├── video.cpp               // Rendu OpenGL
├── audio.cpp               // Audio avec oboe
├── core.cpp                // Interface libretro
├── environment.cpp         // Callbacks environnement
└── renderers/              // Rendu ES2/ES3
```

### Utilité pour RetroPlay:
✅ **Base de RetroPlay:** Toute la logique native vient de là  
✅ **Améliorations RetroPlay:** getAspectRatio, getGameGeometry, onMouseButton  
⚠️ **Attention:** Ne PAS merger aveuglément depuis LibretroDroid officiel (risque d'écraser les ajouts)

---

## 4. common-overlays-master (OVERLAYS OFFICIELS)

**Chemin:** `c:\repos\common-overlays-master`  
**Dernière modif:** 2025-07-26

### Structure:
```
common-overlays-master/
├── gamepads/
│   ├── flat/          ✅ Overlays minimalistes (nes.cfg, psx.cfg, etc.)
│   ├── dual-shock/    ✅ Style PSX (utilisé par RetroPlay)
│   ├── neo-retropad/  ✅ Style moderne
│   ├── arcade/        ✅ Contrôles arcade (6-button, neogeo)
│   └── ... (40+ variantes)
├── borders/           ✅ Bordures décoratives
├── effects/           ✅ Scanlines, CRT, grilles
└── keyboards/         ✅ Claviers virtuels
```

### Overlays pertinents pour RetroPlay:
1. **`gamepads/flat/nes.cfg`** — 12 layouts (landscape-A/B, portrait-A/B, landscape-gb, etc.)
   - `range_mod = 1.5` — Hitbox étendue
   - `alpha_mod = 2.0` — Boutons brillent quand pressés
   - `dpad_area` et `abxy_area` — Zones 8-way

2. **`gamepads/dual-shock/dual-shock.cfg`** — Style PSX avec analog sticks
   - `movable = true` pour analog sticks
   - `saturate_pct = 0.75` — Saturation analog à 75% du rayon

3. **`gamepads/arcade/arcade.cfg`** — 6-button fighter layout

### Utilité pour RetroPlay:
✅ **Source des overlays dans `assets/overlays/`**  
✅ **Référence pour comprendre les propriétés: `reach_x/y`, `exclusive`, `range_mod_exclusive`**

---

## 5. docs-master (DOCUMENTATION OFFICIELLE)

**Chemin:** `c:\repos\docs-master`  
**Dernière modif:** 2025-08-07

### Documents clés consultés:

#### A. Overlays
- **`docs/guides/libretro-overlays.md`**
  - Spec complète du format .cfg
  - `saturate_pct`, `alpha_mod`, `range_mod`, `movable`
  - `reach_x/y/up/down/left/right` pour hitbox extend
  - `exclusive` et `range_mod_exclusive`

- **`docs/guides/overlay-pointing-devices.md`**
  - **Mouse behavior:** 1/2/3 doigts = LMB/RMB/MMB
  - **Lightgun behavior:** Trigger delay pour multi-touch
  - **Swipe threshold:** Distinguer tap vs drag
  - **Trigger delay:** Nécessaire pour cores qui ne bougent pas le curseur instantanément

#### B. Cores
- **`docs/library/fceumm.md`** — Documentation FCEUmm
  - Core options disponibles
  - BIOS optionnels

### Utilité pour RetroPlay:
✅ **Référence pour toutes les fonctionnalités overlay**  
✅ **Explication du comportement attendu**

---

## 6. libretro-database-master (MÉTADONNÉES)

**Chemin:** `c:\repos\libretro-database-master`  
**Dernière modif:** 2025-08-07

### Contenu:
```
libretro-database-master/
├── cht/                    ✅ Cheats (XML format)
│   ├── Nintendo - Nintendo Entertainment System/
│   ├── Sony - PlayStation/
│   └── ... (40+ systèmes)
├── dat/                    ✅ DAT files (No-Intro, Redump)
├── rdb/                    ✅ Bases de données binaires
├── metadat/                ✅ Métadonnées (genre, publisher, year, etc.)
├── cursors/                ✅ Requêtes DB préfabriquées
└── scripts/                ✅ Scripts Python pour génération
```

### Utilité pour RetroPlay:
⚠️ **Potentiel futur:** Système de cheats, métadonnées de jeux  
❌ **Non utilisé actuellement** (RetroPlay ne gère pas les cheats ni les métadonnées)

---

## 7. retroarch-assets-master (ASSETS UI)

**Chemin:** `c:\repos\retroarch-assets-master`  
**Dernière modif:** 2025-05-23

### Contenu:
```
retroarch-assets-master/
├── xmb/                    ✅ Icônes menu XMB (600+ PNG)
├── ozone/                  ✅ Icônes menu Ozone (400+ PNG)
├── glui/                   ✅ Icônes GLUI
├── rgui/                   ✅ Wallpapers et thèmes RGUI
├── fonts/                  ✅ Polices TrueType (DejaVu, Metrophobic, M+, etc.)
├── sounds/                 ✅ Sons UI (click, cancel, launch, bgm.ogg)
├── branding/               ✅ Logos LibRetro/RetroArch
└── wallpapers/             ✅ Fonds d'écran thèmes
```

### Utilité pour RetroPlay:
⚠️ **Potentiel:** Améliorer l'UI avec icônes/sons officiels  
❌ **Non utilisé actuellement** (RetroPlay a son propre UI Material Design)

---

## 8. retroarch-joypad-autoconfig-master (GAMEPAD CONFIGS)

**Chemin:** `c:\repos\retroarch-joypad-autoconfig-master`  
**Dernière modif:** 2025-07-29

### Contenu:
```
android/                    ✅ 200+ configs Android (8BitDo, Xbox, PS4, etc.)
udev/                       ✅ Linux configs
xinput/                     ✅ Windows configs
```

### Exemple: `Sony_DualShock_4_Controller.cfg`
```
input_vendor_id = "1356"
input_product_id = "1476"
input_b_btn = "96"           # Cross
input_a_btn = "97"           # Circle
input_l_btn = "102"          # L1
input_r_btn = "103"          # R1
input_l_x_plus_axis = "+0"
```

### Utilité pour RetroPlay:
✅ **Potentiel:** Auto-configuration des gamepads physiques  
⚠️ **Partiellement utilisé:** RetroPlay gère les gamepads mais sans auto-config fichiers

---

## 9. libretro-super-master (BUILD SYSTEM)

**Chemin:** `c:\repos\libretro-super-master`  
**Dernière modif:** 2025-07-31

### Scripts de build:
```bash
libretro-build-android-arm64_v8a.sh    ✅ Compilation Android ARM64
libretro-build-android-armeabi_v7a.sh  ✅ Compilation Android ARMv7
libretro-fetch.sh                      ✅ Clone tous les cores
libretro-buildbot-recipe.sh            ✅ Build automatisé
```

### Recipes:
```
recipes/android/
├── cores-android-aarch64              ✅ Liste des cores pour ARM64
├── cores-android-aarch64.conf         ✅ Configuration build
└── retroarch-android                  ✅ Build RetroArch Android
```

### Utilité pour RetroPlay:
✅ **Utilisé:** Scripts pour compiler les cores libretro  
⚠️ **Note:** RetroPlay compile ses cores manuellement (NDK direct)

---

## 10. Lemuroid (PROJET ANDROID SIMILAIRE)

**Chemin:** `c:\repos\Lemuroid`  
**Dernière modif:** 2025-10-16

### Caractéristiques:
- **Base:** LibretroDroid (comme RetroPlay)
- **Architecture:** Kotlin + Compose
- **Cores supportés:** 25+ (NES, SNES, PSX, N64, PSP, etc.)
- **UI:** Material Design moderne
- **Features:**
  - Auto-save states
  - ROM scanning et indexing
  - Optimized touch controls
  - Quick save/load
  - Gamepad support

### Modules:
```
lemuroid-touchinput/        ✅ Gestion touch (radial pads)
lemuroid-app/               ✅ App principale
lemuroid-cores/             ✅ Cores précompilés
retrograde-app-shared/      ✅ Logique partagée
```

### Différences avec RetroPlay:
| Feature | Lemuroid | RetroPlay |
|---------|----------|-----------|
| Overlays | Radial touch (custom) | RetroArch .cfg (officiel) |
| UI | Liste + grilles | Custom UI |
| ROM scan | Auto | Manuel |
| Zapper | Non supporté | ✅ Implémenté |

### Utilité pour RetroPlay:
⚠️ **Inspiration:** Architecture Kotlin + Compose  
⚠️ **Touch input:** Approche différente (radial vs RetroArch)  
❌ **Ne PAS copier:** Leur système touch n'est PAS compatible RetroArch

---

## 11. libretro-common-master (BIBLIOTHÈQUE PARTAGÉE)

**Chemin:** `c:\repos\libretro-common-master`  
**Dernière modif:** 2025-08-05

### Contenu:
```
include/libretro.h          ✅ API Libretro complète
audio/                      ✅ Resampling, mixing
file/                       ✅ File I/O, archives
formats/                    ✅ Image, JSON, XML parsers
gfx/                        ✅ Shaders, video utils
queues/                     ✅ Task queues
```

### `include/libretro.h` (CRITIQUE):
- **Constantes input:**
  ```c
  #define RETRO_DEVICE_JOYPAD         1
  #define RETRO_DEVICE_MOUSE          2
  #define RETRO_DEVICE_POINTER        6
  #define RETRO_DEVICE_LIGHTGUN       4
  #define RETRO_DEVICE_ZAPPER         258  // (LIGHTGUN | (1 << 8))
  ```
- **Callbacks environnement:**
  - `RETRO_ENVIRONMENT_GET_SYSTEM_AV_INFO` — Pour getAspectRatio
  - `RETRO_ENVIRONMENT_SET_GEOMETRY` — Changement résolution dynamique

### Utilité pour RetroPlay:
✅ **Utilisé:** LibretroDroid inclut cette bibliothèque  
✅ **Référence:** Pour comprendre l'API Libretro

---

## 12. info/ (CORE METADATA)

**Chemin:** `c:\repos\info`  
**Dernière modif:** 2025-09-03

### Contenu:
300+ fichiers `.info` décrivant chaque core:
- Nom, auteur, licence
- Extensions supportées
- Features (savestates, cheats, netplay, etc.)
- Database associée
- BIOS nécessaires

### Exemple: `fceumm_libretro.info`
```
corename = "FCEUmm"
systemid = "nes"
supported_extensions = "fds|nes|unif|unf"
savestate = "true"
cheats = "true"
database = "Nintendo - Nintendo Entertainment System"
```

### Utilité pour RetroPlay:
⚠️ **Potentiel:** Détection auto des cores, validation extensions  
❌ **Non utilisé actuellement**

---

## 13. glsl-shaders-master + slang-shaders-master (SHADERS)

**Chemin:** `c:\repos\glsl-shaders-master` + `c:\repos\slang-shaders-master`  
**Dernière modif:** 2025-08-17 / 2025-08-14

### Types de shaders:
```
crt/                        ✅ Effets CRT (Royale, Lottes, Aperture Grille)
handheld/                   ✅ Effets LCD (GB, GBA, GG)
scalehq/scalenx/xbr/        ✅ Upscaling pixel-art
ntsc/                       ✅ Artefacts NTSC/composite
scanlines/                  ✅ Scanlines variées
misc/                       ✅ Divers (border, blur, etc.)
```

### Différence GLSL vs Slang:
- **GLSL:** Ancien format (.glsl, .glslp)
- **Slang:** Format moderne (.slang, .slangp) — Vulkan + D3D11/12

### Utilité pour RetroPlay:
⚠️ **Potentiel futur:** Ajout de shaders graphiques avancés  
❌ **Non prioritaire:** RetroPlay utilise déjà quelques shaders basiques

---

## 14. libretro-database (DUPLICATE)

**Chemin:** `c:\repos\libretro-database`  
**Note:** Duplicate de `libretro-database-master` (même contenu)

---

## 15. LibretroDroid-master (DUPLICATE)

**Chemin:** `c:\repos\LibretroDroid-master`  
**Note:** Duplicate de `LibretroDroid` (version plus ancienne)

---

## DÉCOUVERTES IMPORTANTES

### 1. Valeurs par défaut RetroArch (config.def.h)
**IMPACT:** ✅ **UTILISÉ** — Toutes les valeurs par défaut de RetroPlay corrigées

| Variable | Ligne | Valeur |
|----------|-------|--------|
| DEFAULT_INPUT_OVERLAY_OPACITY | 600 | 0.7f |
| DEFAULT_OVERLAY_DPAD_DIAGONAL_SENSITIVITY | 965 | 80 |
| DEFAULT_OVERLAY_ABXY_DIAGONAL_SENSITIVITY | 966 | 50 |
| DEFAULT_OVERLAY_HIDE_IN_MENU | 587 | true |
| DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY | 639 | 1 |
| DEFAULT_INPUT_OVERLAY_MOUSE_HOLD_MSEC | 644 | 200 |

### 2. Formules diagonal sensitivity (input_driver.c:2234-2246)
**IMPACT:** ✅ **IMPLÉMENTÉ** — Formule exacte dans `get8WayDirections()`

```c
float f = 2.0f * diagonal_sensitivity / (100.0f + diagonal_sensitivity);
float high_angle = (f * (0.375 * M_PI) + (1.0f - f) * (0.25 * M_PI));  // 67.5 deg
float low_angle = (f * (0.125 * M_PI) + (1.0f - f) * (0.25 * M_PI));   // 22.5 deg
*high_slope = tan(high_angle);
*low_slope = tan(low_angle);
```

### 3. Analog recenter zone (input_driver.c:2370-2398)
**IMPACT:** ✅ **IMPLÉMENTÉ** — Logique exacte dans `handleTouchEvent()`

```c
if (first_touch && recenter_zone != 0) {
    float radius = sqrt(desc->range_x^2 + desc->range_y^2);
    float dist = sqrt((*x_dist)^2 + (*y_dist)^2);
    if (dist <= radius * (recenter_zone / 100.0f)) {
        x_center[b] = x;
        y_center[b] = y;
    }
}
```

### 4. Zapper NES (libretro.c:2442-2455)
**IMPACT:** ✅ **IMPLÉMENTÉ** — Conversion exacte dans `handleZapperTouch()`

```c
int offset_x = (crop_overscan_h_left * 0x120) - 1;
int offset_y = (crop_overscan_v_top * 0x133) + 1;
mousedata[0] = ((mouse_x + (32767 + offset_x)) * max_width) / ((32767 + offset_x) * 2);
mousedata[1] = ((mouse_y + (32767 + offset_y)) * max_height) / ((32767 + offset_y) * 2);
```

### 5. Hide in menu (input_driver.c:5330-5331)
**IMPACT:** ✅ **IMPLÉMENTÉ** — Logique dans `RetroArchEmulatorActivity.kt`

```c
if (settings->bools.input_overlay_hide_in_menu)
    hide = (menu_state_get_ptr()->flags & MENU_ST_FLAG_ALIVE) != 0;
```

### 6. Lightgun multi-touch (input_driver.c:3163-3170)
**IMPACT:** ✅ **IMPLÉMENTÉ** — `handleMultiTouchActions()` dans RetroPlay

```c
switch (peak_ptr_count) {
    case 2: action = settings->uints.input_overlay_lightgun_two_touch_input; break;
    case 3: action = settings->uints.input_overlay_lightgun_three_touch_input; break;
    case 4: action = settings->uints.input_overlay_lightgun_four_touch_input; break;
}
```

---

## COMPARAISON: LibretroDroid OFFICIEL vs FORK RetroPlay

### Fonctions AJOUTÉES dans la fork RetroPlay:

| Fonction | Fichier | Utilité |
|----------|---------|---------|
| `getAspectRatio()` | libretrodroid.cpp:487 | Ratio réel du core (1.306 pour NES) |
| `getGameGeometryWidth()` | libretrodroid.cpp:492 | Largeur rendu (256 pour NES) |
| `getGameGeometryHeight()` | libretrodroid.cpp:498 | Hauteur rendu (224/240 pour NES) |
| `onMouseButton()` | input.cpp:129 | Support trigger Zapper |

### ⚠️ IMPORTANT:
Ces fonctions n'existent PAS dans LibretroDroid officiel!  
**Ne jamais merger aveuglément depuis c:\repos\LibretroDroid** → Risque de perdre ces ajouts!

---

## RESSOURCES INUTILISÉES (Potentiel futur)

### 1. Système de cheats (libretro-database-master/cht/)
- Format XML avec codes Action Replay/Game Genie
- 40+ systèmes couverts
- **Potentiel:** Ajouter menu "Cheat Codes" dans RetroPlay

### 2. Métadonnées jeux (libretro-database-master/metadat/)
- Genre, publisher, release year, ratings
- **Potentiel:** Afficher infos de jeux dans l'UI

### 3. Shaders avancés (glsl-shaders-master, slang-shaders-master)
- CRT-Royale, Mega Bezel, LCD-grid
- **Potentiel:** Menu de sélection de shaders graphiques

### 4. Assets UI (retroarch-assets-master)
- Icônes SVG/PNG haute qualité
- Sons UI (click, launch, bgm)
- **Potentiel:** Améliorer l'esthétique de RetroPlay

### 5. Auto-config gamepad (retroarch-joypad-autoconfig-master)
- Détection automatique de 200+ gamepads
- **Potentiel:** Plug-and-play pour gamepads physiques

---

## FICHIERS SOURCES CONSULTÉS POUR RETROPLAY

### Pour Zapper NES:
1. ✅ `libretro-fceumm-master/src/input/zapper.c` (lignes 66-113)
2. ✅ `libretro-fceumm-master/src/drivers/libretro/libretro.c` (lignes 2442-2455)
3. ✅ `RetroArch-master/gfx/video_driver.c` — `video_driver_translate_coord_viewport()`

### Pour Advanced Overlay Settings:
1. ✅ `RetroArch-master/config.def.h` (lignes 580-648)
2. ✅ `RetroArch-master/configuration.h` (lignes 335-720)
3. ✅ `RetroArch-master/input/input_driver.c` (lignes 2230-5340)
4. ✅ `RetroArch-master/input/input_overlay.h`
5. ✅ `docs-master/docs/guides/overlay-pointing-devices.md`

### Pour overlays .cfg:
1. ✅ `common-overlays-master/gamepads/flat/nes.cfg`
2. ✅ `common-overlays-master/gamepads/dual-shock/dual-shock.cfg`
3. ✅ `docs-master/docs/development/retroarch/input/overlay.md`

---

## RECOMMANDATIONS

### ✅ À utiliser maintenant:
1. **config.def.h** — Référence pour TOUTES les valeurs par défaut
2. **input_driver.c** — Formules mathématiques et logiques
3. **libretro.c (fceumm)** — Conversion coordonnées Zapper
4. **overlay docs** — Spec complète pour overlays

### ⚠️ À considérer pour le futur:
1. **Cheats database** — Système de triche intégré
2. **Shaders avancés** — CRT, LCD, upscaling
3. **Auto-config gamepad** — Plug-and-play
4. **Métadonnées** — Infos de jeux dans l'UI

### ❌ À ignorer:
1. **libretro-super build scripts** — RetroPlay compile directement avec NDK
2. **LibretroDroid officiel** — RetroPlay a sa fork custom (ne PAS merger!)
3. **Lemuroid touch input** — Incompatible avec RetroArch overlays

---

## CONCLUSION

### Total exploré: 16 répertoires

### Utilisés activement dans RetroPlay:
1. ✅ **RetroArch-master** — Valeurs défaut, formules, logiques
2. ✅ **libretro-fceumm-master** — Zapper NES, core compilé
3. ✅ **common-overlays-master** — Overlays dans assets/
4. ✅ **docs-master** — Documentation référence
5. ✅ **LibretroDroid** (fork) — Base native de RetroPlay

### Ressources disponibles (non utilisées):
6. ⚠️ **libretro-database-master** — Cheats, métadonnées
7. ⚠️ **retroarch-assets-master** — Icônes, fonts, sons
8. ⚠️ **retroarch-joypad-autoconfig-master** — Auto-config gamepads
9. ⚠️ **glsl/slang-shaders-master** — Shaders graphiques
10. ⚠️ **libretro-super-master** — Build system
11. ⚠️ **Lemuroid** — Inspiration architecture

### Ressources ignorées (duplicates):
12. ❌ **libretro-database** — Duplicate
13. ❌ **LibretroDroid-master** — Duplicate (version ancienne)

### Ressources explorées (référence):
14. ✅ **libretro-common-master** — API Libretro
15. ✅ **info/** — Métadonnées cores

---

**STATUS:** ✅ AUDIT COMPLET TERMINÉ — 16/16 répertoires explorés et documentés

